package com.mrms.claim.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.claim.ClaimStatus;
import com.mrms.claim.ClaimStatusChanged;
import com.mrms.claim.internal.ClaimDtos.AttachmentView;
import com.mrms.claim.internal.ClaimDtos.ClaimSummary;
import com.mrms.claim.internal.ClaimDtos.ClaimView;
import com.mrms.claim.internal.ClaimDtos.ItemView;
import com.mrms.claim.internal.ClaimDtos.NacLink;
import com.mrms.claim.internal.ClaimDtos.TimelineEntry;
import com.mrms.claim.internal.ClaimEnums.ItemCategory;
import com.mrms.claim.internal.ClaimEnums.ReturnReason;
import com.mrms.document.DocumentCategory;
import com.mrms.document.DocumentMeta;
import com.mrms.document.DocumentStore;
import com.mrms.enac.NacItemRef;
import com.mrms.enac.NacLookup;
import com.mrms.identity.Accounts;
import com.mrms.organisation.OrganisationDirectory;
import com.mrms.rates.RateBasis;
import com.mrms.rates.RateLookup;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import com.mrms.shared.web.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cross cutting helpers of the claim module: who may see a claim, SLA
 * calculation, timeline and event recording, and mapping to views.
 */
@Component
class ClaimSupport {

    private final ClaimRepository claims;
    private final ClaimEventRepository timeline;
    private final DocumentStore documents;
    private final NacLookup nac;
    private final OrganisationDirectory organisation;
    private final Accounts accounts;
    private final AuditTrail audit;
    private final RateLookup rates;
    private final ApplicationEventPublisher events;
    private final MrmsProperties props;
    private final Clock clock;

    ClaimSupport(ClaimRepository claims, ClaimEventRepository timeline, DocumentStore documents, NacLookup nac,
                 OrganisationDirectory organisation, Accounts accounts, AuditTrail audit, RateLookup rates,
                 ApplicationEventPublisher events, MrmsProperties props, Clock clock) {
        this.claims = claims;
        this.timeline = timeline;
        this.documents = documents;
        this.nac = nac;
        this.organisation = organisation;
        this.accounts = accounts;
        this.audit = audit;
        this.rates = rates;
        this.events = events;
        this.props = props;
        this.clock = clock;
    }

    Instant now() {
        return clock.instant();
    }

    // ------------------------------------------------------------------
    // Access control (object level)
    // ------------------------------------------------------------------

    /**
     * Returns the claim only if the current user may see it. A claim that
     * exists but is out of scope is reported as not found so that ids cannot
     * be probed.
     */
    Claim visibleClaim(Long id) {
        MrmsPrincipal me = CurrentUser.get();
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim"));
        if (!canSee(me, claim)) {
            throw new NotFoundException("Claim");
        }
        return claim;
    }

    static boolean canSee(MrmsPrincipal me, Claim claim) {
        return switch (me.role()) {
            case EMPLOYEE -> claim.getEmployeeUserId().equals(me.userId());
            case HOS -> claim.getSchoolId().equals(me.schoolId()) && claim.getStatus() != ClaimStatus.DRAFT;
            case PAO_AUDITOR, PAO_OFFICER -> claim.getPaoId().equals(me.paoId()) && claim.getHosCertifiedAt() != null;
            default -> false;
        };
    }

    /** Every document id that belongs to a claim, including prescriptions of linked e-NACs. */
    Set<UUID> documentIdsOf(Claim claim) {
        Set<UUID> ids = new HashSet<>();
        claim.getItems().forEach(i -> ids.add(i.getBillDocumentId()));
        claim.getAttachments().forEach(a -> ids.add(a.getDocumentId()));
        List<Long> nacItemIds = claim.getItems().stream().map(ClaimItem::getNacItemId).filter(Objects::nonNull).toList();
        nac.items(nacItemIds).forEach(r -> ids.add(r.prescriptionDocumentId()));
        return ids;
    }

    /**
     * Standard name of every document of a claim, numbered per type in a
     * stable order: bills by line, then attachments, then e-NAC prescriptions.
     */
    Map<UUID, String> documentNames(Claim claim, Map<UUID, DocumentMeta> metas) {
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<DocumentCategory, Integer> counters = new java.util.EnumMap<>(DocumentCategory.class);
        List<UUID> ordered = new ArrayList<>();
        claim.getItems().stream().sorted(java.util.Comparator.comparingInt(ClaimItem::getLineNo))
                .forEach(i -> ordered.add(i.getBillDocumentId()));
        claim.getAttachments().forEach(a -> ordered.add(a.getDocumentId()));
        List<Long> nacItemIds = claim.getItems().stream().map(ClaimItem::getNacItemId).filter(Objects::nonNull).toList();
        nac.items(nacItemIds).forEach(r -> ordered.add(r.prescriptionDocumentId()));
        for (UUID id : ordered) {
            DocumentMeta meta = metas.get(id);
            if (meta == null || names.containsKey(id)) {
                continue;
            }
            int seq = counters.merge(meta.category(), 1, Integer::sum);
            names.put(id, documents.claimFileName(claim.getClaimNumber(), meta.category(), seq, meta.contentType()));
        }
        return names;
    }

    Map<UUID, String> documentNames(Claim claim) {
        Map<UUID, DocumentMeta> metas = documents.metas(documentIdsOf(claim)).stream()
                .collect(Collectors.toMap(DocumentMeta::id, Function.identity()));
        return documentNames(claim, metas);
    }

    // ------------------------------------------------------------------
    // SLA
    // ------------------------------------------------------------------

    int slaDays(ClaimStatus status) {
        return switch (status) {
            case PENDING_HOS -> props.sla().hosDays();
            case PENDING_PAO_AUDIT -> props.sla().paoAuditDays();
            case PENDING_SANCTION -> props.sla().sanctionDays();
            default -> 0;
        };
    }

    boolean overdue(Claim c) {
        int days = slaDays(c.getStatus());
        return days > 0 && c.getStageEnteredAt() != null
                && c.getStageEnteredAt().plus(Duration.ofDays(days)).isBefore(clock.instant());
    }

    // ------------------------------------------------------------------
    // Recording a transition
    // ------------------------------------------------------------------

    /**
     * Writes the timeline entry and the audit entry and publishes the event,
     * all inside the caller's transaction.
     */
    void recordTransition(Claim claim, String action, ClaimStatus from, String remarks,
                          Collection<ReturnReason> reasons) {
        MrmsPrincipal me = CurrentUser.get();
        String reasonCodes = reasons == null || reasons.isEmpty() ? null
                : reasons.stream().map(Enum::name).collect(Collectors.joining(","));
        timeline.save(new ClaimEvent(claim.getId(), action, from, claim.getStatus(), me.userId(), me.fullName(),
                me.role().label(), blankToNull(remarks), reasonCodes, clock.instant()));
        audit.record("CLAIM_" + action, "CLAIM", claim.getId(),
                claim.getClaimNumber() + ": " + from + " -> " + claim.getStatus()
                        + (reasonCodes == null ? "" : " [" + reasonCodes + "]"));
        events.publishEvent(new ClaimStatusChanged(claim.getId(), claim.getClaimNumber(), claim.getEmployeeUserId(),
                claim.getSchoolId(), claim.getPaoId(), from, claim.getStatus(), claim.getAssignedTo(),
                blankToNull(remarks)));
    }

    /**
     * Signatures on the claim for the printed copy. Signing actions are
     * confirmed with the signer's password (step up) unless Aadhaar eSign
     * is configured; either way the name and time come from the timeline.
     */
    List<ClaimPdf.SignatureLine> signatureLines(Claim claim) {
        Map<String, String> purposes = Map.of(
                "FORWARDED_BY_HOS", "Head of School certificate",
                "SANCTIONED", "Sanction",
                "REJECTED", "Rejection");
        return timeline.findByClaimIdOrderByOccurredAtAscIdAsc(claim.getId()).stream()
                .filter(e -> purposes.containsKey(e.getAction()))
                .map(e -> new ClaimPdf.SignatureLine(purposes.get(e.getAction()), e.getActorName(),
                        "Password confirmation", e.getOccurredAt(), e.getActorRole()))
                .toList();
    }

    /** Audit only entry for actions that do not change the status (take, release). */
    void auditOnly(Claim claim, String action) {
        audit.record("CLAIM_" + action, "CLAIM", claim.getId(), claim.getClaimNumber());
    }

    // ------------------------------------------------------------------
    // Views
    // ------------------------------------------------------------------

    List<ClaimSummary> summaries(List<Claim> list) {
        Map<Long, String> names = accounts.fullNames(list.stream()
                .flatMap(c -> Stream.of(c.getEmployeeUserId(), c.getAssignedTo()))
                .filter(Objects::nonNull).toList());
        return list.stream().map(c -> new ClaimSummary(c.getId(), c.getClaimNumber(), c.getStatus(),
                c.getStatus().label(), names.get(c.getEmployeeUserId()), c.getPatientName(), c.getPatientRelation(),
                c.getTreatmentType(), c.getClaimedAmount(), c.getRestrictedAmount(), c.getAdmittedAmount(),
                c.getCreatedAt(), c.getFirstSubmittedAt(), c.getStageEnteredAt(), overdue(c),
                c.getAssignedTo() == null ? null : names.get(c.getAssignedTo()), c.getReturnCount())).toList();
    }

    ClaimView view(Claim c, List<String> allowedActions) {
        MrmsPrincipal me = CurrentUser.get();

        List<Long> people = Stream.of(c.getAssignedTo(), c.getHosCertifiedBy(), c.getAuditedBy(), c.getSanctionedBy())
                .filter(Objects::nonNull).toList();
        Map<Long, String> names = accounts.fullNames(people);

        // Documents and e-NAC links in two batched lookups
        Map<UUID, DocumentMeta> docs = documents.metas(documentIdsOf(c)).stream()
                .collect(Collectors.toMap(DocumentMeta::id, Function.identity()));
        Map<Long, NacItemRef> nacItems = nac.items(c.getItems().stream().map(ClaimItem::getNacItemId)
                        .filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(NacItemRef::itemId, Function.identity()));
        Map<Long, String> dispensaryNames = organisation.dispensaryNames(nacItems.values().stream()
                .map(NacItemRef::dispensaryId).collect(Collectors.toSet()));

        var profile = organisation.profileOf(c.getEmployeeUserId()).orElse(null);
        String ward = profile == null ? null : profile.wardEntitlement();
        boolean indoor = c.getTreatmentType() == ClaimEnums.TreatmentType.INDOOR;

        List<ItemView> items = c.getItems().stream().map(i -> {
            NacItemRef ref = i.getNacItemId() == null ? null : nacItems.get(i.getNacItemId());
            NacLink link = ref == null ? null : new NacLink(ref.nacRequestId(), ref.nacNumber(), ref.itemName(),
                    ref.decision(), dispensaryNames.get(ref.dispensaryId()), ref.prescriptionDocumentId(),
                    ref.prescriptionDate());
            return new ItemView(i.getId(), i.getLineNo(), i.getCategory(), i.getDescription(), i.getBillNumber(),
                    i.getBillDate(), i.getVendorName(), i.getDgehsCode(), i.getAmountClaimed(), i.getDgehsRate(),
                    i.getAmountRestricted(), i.getHosRemarks(), i.getRateReference(), i.getAmountAdmitted(),
                    i.getDisallowReason(), i.getNacItemId(), i.isLegacyNac(), link, docs.get(i.getBillDocumentId()),
                    quotes(i, ward, indoor));
        }).toList();

        List<AttachmentView> attachments = c.getAttachments().stream()
                .map(a -> new AttachmentView(a.getCategory(), docs.get(a.getDocumentId()))).toList();

        List<TimelineEntry> history = timeline.findByClaimIdOrderByOccurredAtAscIdAsc(c.getId()).stream()
                .map(e -> new TimelineEntry(e.getAction(), e.getFromStatus(), e.getToStatus(), e.getActorName(),
                        e.getActorRole(), e.getRemarks(), reasonLabels(e.getReasonCodes()), e.getOccurredAt()))
                .toList();

        return new ClaimView(c.getId(), c.getClaimNumber(), c.getStatus(), c.getStatus().label(),
                profile,
                c.getPatientName(), c.getPatientRelation(), c.getDependentId(), c.getIllnessDescription(),
                c.getTreatmentType(), c.getTreatmentFrom(), c.getTreatmentTo(), c.getAdmissionDate(),
                c.getDischargeDate(), c.getHospitalName(), c.getHospitalAddress(), c.getHospitalType(),
                c.isEmergency(), c.getReferralDetails(), c.getMedicalAdvanceDetails(), c.getClaimedAmount(),
                c.getRestrictedAmount(), c.getAdmittedAmount(), totals(c), items, attachments,
                checklist(c, nacItems.values()), history, c.getFinancialYear(), c.getCreatedAt(),
                c.getFirstSubmittedAt(), c.getLastSubmittedAt(), c.getStageEnteredAt(), queuePosition(c), overdue(c),
                slaDays(c.getStatus()),
                c.getAssignedTo() == null ? null : names.get(c.getAssignedTo()),
                me.userId().equals(c.getAssignedTo()), c.getReturnCount(),
                name(names, c.getHosCertifiedBy()), c.getHosCertifiedAt(),
                name(names, c.getAuditedBy()), c.getAuditedAt(), c.getAuditRecommendation(),
                name(names, c.getSanctionedBy()), c.getSanctionedAt(), c.getPaidAt(), c.getPaymentBatchRef(),
                c.getRejectionReason(), c.getRateBasis(), c.suggestedRateBasis(), RATE_BASES,
                documentNames(c, docs), allowedActions);
    }

    private static final List<ClaimDtos.Option> RATE_BASES = java.util.Arrays.stream(RateBasis.values())
            .map(b -> new ClaimDtos.Option(b.name(), b.label())).toList();

    /** Applicable rate of an item under every rate column, so the school can switch basis instantly. */
    Map<RateBasis, RateLookup.RateQuote> quotes(ClaimItem item, String ward, boolean indoor) {
        if (item.getDgehsCode() == null || item.getDgehsCode().isBlank()) {
            return Map.of();
        }
        Map<RateBasis, RateLookup.RateQuote> result = new java.util.EnumMap<>(RateBasis.class);
        for (RateBasis basis : List.of(RateBasis.NABH, RateBasis.NON_NABH, RateBasis.SUPER_SPECIALITY)) {
            rates.quote(item.getDgehsCode(), item.getBillDate(), basis, ward, indoor)
                    .ifPresent(q -> result.put(basis, q));
        }
        return result;
    }

    Optional<RateLookup.RateQuote> quote(ClaimItem item, RateBasis basis, String ward, boolean indoor) {
        return rates.quote(item.getDgehsCode(), item.getBillDate(), basis, ward, indoor);
    }

    /** Annexure II table: amount per category, split into OPD and indoor. */
    private static Map<String, Map<String, BigDecimal>> totals(Claim c) {
        Map<String, Map<String, BigDecimal>> result = new LinkedHashMap<>();
        for (ItemCategory category : ItemCategory.values()) {
            BigDecimal sum = c.getItems().stream().filter(i -> i.getCategory() == category)
                    .map(ClaimItem::getAmountClaimed).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, BigDecimal> split = new LinkedHashMap<>();
            boolean indoor = c.getTreatmentType() == ClaimEnums.TreatmentType.INDOOR;
            split.put("OPD", indoor ? BigDecimal.ZERO : sum);
            split.put("INDOOR", indoor ? sum : BigDecimal.ZERO);
            result.put(category.name(), split);
        }
        return result;
    }

    private static List<ClaimDtos.ChecklistEntry> checklist(Claim c, Collection<NacItemRef> nacItems) {
        Set<DocumentCategory> attached = c.getAttachments().stream().map(ClaimAttachment::getCategory)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DocumentCategory.class)));
        Set<ItemCategory> categories = c.getItems().stream().map(ClaimItem::getCategory)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ItemCategory.class)));
        boolean medicinesCovered = c.getItems().stream().filter(i -> i.getCategory() == ItemCategory.MEDICINE)
                .allMatch(i -> i.getNacItemId() != null || i.isLegacyNac());
        boolean allBills = c.getItems().stream().allMatch(i -> i.getBillDocumentId() != null)
                && !c.getItems().isEmpty();
        return ClaimTexts.checklist(attached, c.getTreatmentType(), c.isEmergency(), categories, allBills,
                !nacItems.isEmpty(), medicinesCovered);
    }

    private Integer queuePosition(Claim c) {
        List<Claim> queue = switch (c.getStatus()) {
            case PENDING_HOS -> claims.findBySchoolIdAndStatusOrderByFirstSubmittedAtAscIdAsc(c.getSchoolId(),
                    c.getStatus());
            case PENDING_PAO_AUDIT, PENDING_SANCTION -> claims.findByPaoIdAndStatusOrderByFirstSubmittedAtAscIdAsc(
                    c.getPaoId(), c.getStatus());
            default -> List.of();
        };
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId().equals(c.getId())) {
                return i + 1;
            }
        }
        return null;
    }

    private static List<String> reasonLabels(String codes) {
        if (codes == null || codes.isBlank()) {
            return List.of();
        }
        List<String> labels = new ArrayList<>();
        Arrays.stream(codes.split(",")).forEach(code -> {
            try {
                labels.add(ReturnReason.valueOf(code).label);
            } catch (IllegalArgumentException e) {
                labels.add(code);
            }
        });
        return labels;
    }

    private static String name(Map<Long, String> names, Long id) {
        return id == null ? null : names.get(id);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
