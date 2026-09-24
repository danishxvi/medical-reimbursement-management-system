package com.mrms.enac.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.document.DocumentCategory;
import com.mrms.document.DocumentMeta;
import com.mrms.document.DocumentStore;
import com.mrms.enac.NacItemRef;
import com.mrms.enac.NacLookup;
import com.mrms.enac.NacStatusChanged;
import com.mrms.enac.NacTypes.Decision;
import com.mrms.enac.NacTypes.NacStatus;
import com.mrms.enac.internal.NacDtos.CreateNacRequest;
import com.mrms.enac.internal.NacDtos.ItemDecisionInput;
import com.mrms.enac.internal.NacDtos.ItemInput;
import com.mrms.enac.internal.NacDtos.ItemView;
import com.mrms.enac.internal.NacDtos.NacSummary;
import com.mrms.enac.internal.NacDtos.NacView;
import com.mrms.enac.internal.NacDtos.PharmacistReviewRequest;
import com.mrms.enac.internal.NacDtos.QueueView;
import com.mrms.enac.internal.NacDtos.ResubmitNacRequest;
import com.mrms.identity.Accounts;
import com.mrms.organisation.OrganisationDirectory;
import com.mrms.organisation.OrganisationViews.DependentView;
import com.mrms.organisation.OrganisationViews.OfficeRef;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.domain.FinancialYear;
import com.mrms.shared.domain.Relation;
import com.mrms.shared.domain.Role;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.ForbiddenException;
import com.mrms.shared.web.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
class NacService implements NacLookup {

    /** A prescription older than this cannot be brought for a certificate. */
    private static final int MAX_PRESCRIPTION_AGE_DAYS = 180;

    private final NacRepository repository;
    private final DocumentStore documents;
    private final OrganisationDirectory organisation;
    private final Accounts accounts;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final MrmsProperties props;
    private final Clock clock;

    NacService(NacRepository repository, DocumentStore documents, OrganisationDirectory organisation,
               Accounts accounts, AuditTrail audit, ApplicationEventPublisher events, MrmsProperties props,
               Clock clock) {
        this.repository = repository;
        this.documents = documents;
        this.organisation = organisation;
        this.accounts = accounts;
        this.audit = audit;
        this.events = events;
        this.props = props;
        this.clock = clock;
    }

    // ==================================================================
    // Employee actions
    // ==================================================================

    @Transactional
    NacView create(CreateNacRequest body) {
        MrmsPrincipal me = requireRole(Role.EMPLOYEE);
        organisation.dispensary(body.dispensaryId())
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_DISPENSARY", "Select a valid dispensary"));
        validatePrescription(me, body.prescriptionDate(), body.prescriptionDocumentId());

        String patientName = me.fullName();
        Relation relation = Relation.SELF;
        if (body.dependentId() != null) {
            DependentView dependent = organisation.dependentOf(me.userId(), body.dependentId())
                    .orElseThrow(() -> new BusinessRuleException("UNKNOWN_DEPENDENT",
                            "Select a dependent from your profile"));
            patientName = dependent.fullName();
            relation = dependent.relation();
        }

        NacRequest request = new NacRequest(me.userId(), body.dispensaryId(), patientName, relation,
                body.dependentId(), body.prescriptionDate(), body.prescribedBy().trim(),
                body.prescriptionDocumentId(), toItems(body.items()), clock.instant());
        repository.save(request);
        audit.record("NAC_REQUESTED", "NAC", request.getId(), request.getItems().size() + " items");
        publish(request, null);
        return view(request);
    }

    @Transactional
    NacView resubmit(Long id, ResubmitNacRequest body) {
        MrmsPrincipal me = requireRole(Role.EMPLOYEE);
        NacRequest request = ownRequest(id, me);
        validatePrescription(me, body.prescriptionDate(), body.prescriptionDocumentId());
        request.resubmit(me.userId(), body.prescriptionDate(), body.prescribedBy().trim(),
                body.prescriptionDocumentId(), toItems(body.items()), clock.instant());
        audit.record("NAC_RESUBMITTED", "NAC", id, null);
        publish(request, null);
        return view(request);
    }

    @Transactional(readOnly = true)
    List<NacSummary> mine() {
        MrmsPrincipal me = requireRole(Role.EMPLOYEE);
        return summaries(repository.findByEmployeeUserIdOrderByCreatedAtDesc(me.userId()));
    }

    // ==================================================================
    // Dispensary queue (pharmacist and medical officer)
    // ==================================================================

    @Transactional(readOnly = true)
    QueueView queue() {
        MrmsPrincipal me = requireDispensaryStaff();
        NacStatus stage = stageOf(me);
        List<NacRequest> waiting = repository.findByDispensaryIdAndStatusOrderByQueueSinceAscIdAsc(
                me.dispensaryId(), stage);
        NacView current = waiting.stream()
                .filter(r -> me.userId().equals(r.getAssignedTo()))
                .findFirst().map(this::view).orElse(null);
        return new QueueView(stage, slaDays(stage), current, summaries(waiting));
    }

    /**
     * Strict first come first served: the official cannot choose. If they
     * already hold a request, that one is returned; otherwise the oldest
     * untaken request is assigned to them.
     */
    @Transactional
    NacView takeNext() {
        MrmsPrincipal me = requireDispensaryStaff();
        NacStatus stage = stageOf(me);
        Optional<NacRequest> held = repository.findFirstByDispensaryIdAndStatusAndAssignedTo(
                me.dispensaryId(), stage, me.userId());
        if (held.isPresent()) {
            return view(held.get());
        }
        NacRequest next = repository.findByDispensaryIdAndStatusAndAssignedToIsNullOrderByQueueSinceAscIdAsc(
                        me.dispensaryId(), stage, Limit.of(1)).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("QUEUE_EMPTY", "There is nothing waiting in your queue"));
        next.assignTo(me.userId(), clock.instant());
        repository.saveAndFlush(next);
        audit.record("NAC_TAKEN", "NAC", next.getId(), stage.name());
        return view(next);
    }

    @Transactional
    void release(Long id) {
        MrmsPrincipal me = requireDispensaryStaff();
        NacRequest request = dispensaryRequest(id, me);
        request.release(me.userId(), clock.instant());
        audit.record("NAC_RELEASED", "NAC", id, null);
    }

    @Transactional
    NacView pharmacistReview(Long id, PharmacistReviewRequest body) {
        MrmsPrincipal me = requireRole(Role.PHARMACIST);
        NacRequest request = dispensaryRequest(id, me);
        Instant now = clock.instant();

        Map<Long, NacItem> byId = request.getItems().stream()
                .collect(Collectors.toMap(NacItem::getId, Function.identity()));
        for (ItemDecisionInput d : body.decisions()) {
            NacItem item = byId.get(d.itemId());
            if (item == null) {
                throw new BusinessRuleException("UNKNOWN_ITEM", "An item does not belong to this prescription");
            }
            if (d.decision() == Decision.NOT_ADMISSIBLE && (d.reason() == null || d.reason().isBlank())) {
                throw new BusinessRuleException("REASON_REQUIRED",
                        "Give a reason for every item marked not admissible");
            }
            item.decide(d.decision(), blankToNull(d.reason()), me.userId(), now);
        }
        request.completePharmacistReview(me.userId(), blankToNull(body.remarks()), now);
        audit.record("NAC_PHARMACIST_REVIEWED", "NAC", id, decisionSummary(request));
        publish(request, body.remarks());
        return view(request);
    }

    @Transactional
    NacView pharmacistReturn(Long id, String remarks) {
        MrmsPrincipal me = requireRole(Role.PHARMACIST);
        NacRequest request = dispensaryRequest(id, me);
        request.returnToEmployee(me.userId(), remarks.trim(), clock.instant());
        audit.record("NAC_RETURNED", "NAC", id, remarks);
        publish(request, remarks);
        return view(request);
    }

    @Transactional
    NacView countersign(Long id, String password, String remarks) {
        MrmsPrincipal me = requireRole(Role.MEDICAL_OFFICER);
        // Legally significant: confirm identity before signing
        accounts.confirmPassword(password);
        NacRequest request = dispensaryRequest(id, me);
        String dispensaryCode = organisation.dispensary(me.dispensaryId()).map(OfficeRef::code).orElse("NA");
        String number = "NAC/" + dispensaryCode + "/" + FinancialYear.current() + "/"
                + String.format("%06d", repository.nextNumber());
        request.countersign(me.userId(), blankToNull(remarks), number, clock.instant());
        audit.record("NAC_ISSUED", "NAC", id, number + "; " + decisionSummary(request));
        publish(request, remarks);
        return view(request);
    }

    @Transactional
    NacView sendBack(Long id, String remarks) {
        MrmsPrincipal me = requireRole(Role.MEDICAL_OFFICER);
        NacRequest request = dispensaryRequest(id, me);
        request.sendBackToPharmacist(me.userId(), remarks.trim(), clock.instant());
        audit.record("NAC_SENT_BACK", "NAC", id, remarks);
        publish(request, remarks);
        return view(request);
    }

    // ==================================================================
    // Viewing
    // ==================================================================

    @Transactional(readOnly = true)
    NacView get(Long id) {
        return view(visibleRequest(id));
    }

    @Transactional
    DocumentStore.DocumentContent prescription(Long id) {
        NacRequest request = visibleRequest(id);
        return documents.read(request.getPrescriptionDocumentId());
    }

    /** Employee sees own requests; dispensary staff see requests made to their dispensary. */
    private NacRequest visibleRequest(Long id) {
        MrmsPrincipal me = CurrentUser.get();
        NacRequest request = repository.findById(id).orElseThrow(() -> new NotFoundException("Certificate"));
        boolean allowed = switch (me.role()) {
            case EMPLOYEE -> request.getEmployeeUserId().equals(me.userId());
            case PHARMACIST, MEDICAL_OFFICER -> request.getDispensaryId().equals(me.dispensaryId());
            default -> false;
        };
        if (!allowed) {
            throw new NotFoundException("Certificate");
        }
        return request;
    }

    // ==================================================================
    // NacLookup (module API)
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public List<NacItemRef> items(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        List<NacItemRef> result = new ArrayList<>();
        for (NacRequest r : repository.findContainingItems(itemIds)) {
            r.getItems().stream().filter(i -> itemIds.contains(i.getId())).forEach(i -> result.add(ref(r, i)));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NacItemRef> claimableItems(Long employeeUserId) {
        return repository.findIssuedForEmployee(employeeUserId).stream()
                .flatMap(r -> r.getItems().stream().map(i -> ref(r, i)))
                .filter(NacItemRef::claimable)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsByStatusForEmployee(Long employeeUserId) {
        return toCounts(repository.countByStatusForEmployee(employeeUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsByStatusForDispensary(Long dispensaryId) {
        Map<String, Long> counts = toCounts(repository.countByStatusForDispensary(dispensaryId));
        Instant now = clock.instant();
        counts.put("OVERDUE_PHARMACIST", repository.countByDispensaryIdAndStatusAndStageEnteredAtBefore(dispensaryId,
                NacStatus.PENDING_PHARMACIST, now.minus(Duration.ofDays(slaDays(NacStatus.PENDING_PHARMACIST)))));
        counts.put("OVERDUE_MEDICAL_OFFICER", repository.countByDispensaryIdAndStatusAndStageEnteredAtBefore(
                dispensaryId, NacStatus.PENDING_MEDICAL_OFFICER,
                now.minus(Duration.ofDays(slaDays(NacStatus.PENDING_MEDICAL_OFFICER)))));
        return counts;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private void validatePrescription(MrmsPrincipal me, LocalDate prescriptionDate, UUID documentId) {
        LocalDate today = LocalDate.now(FinancialYear.IST);
        if (prescriptionDate.isAfter(today) || prescriptionDate.isBefore(today.minusDays(MAX_PRESCRIPTION_AGE_DAYS))) {
            throw new BusinessRuleException("PRESCRIPTION_DATE",
                    "The prescription must be dated within the last " + MAX_PRESCRIPTION_AGE_DAYS + " days");
        }
        DocumentMeta doc = documents.meta(documentId)
                .filter(m -> m.ownerUserId().equals(me.userId()))
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_DOCUMENT", "Upload the prescription first"));
        if (doc.category() != DocumentCategory.PRESCRIPTION) {
            throw new BusinessRuleException("WRONG_DOCUMENT", "The uploaded file is not marked as a prescription");
        }
    }

    private static List<NacItem> toItems(List<ItemInput> inputs) {
        List<NacItem> items = new ArrayList<>();
        int line = 1;
        for (ItemInput in : inputs) {
            items.add(new NacItem(line++, in.itemName().trim(), in.itemType(), in.quantity().trim()));
        }
        return items;
    }

    private NacRequest ownRequest(Long id, MrmsPrincipal me) {
        return repository.findById(id)
                .filter(r -> r.getEmployeeUserId().equals(me.userId()))
                .orElseThrow(() -> new NotFoundException("Certificate"));
    }

    private NacRequest dispensaryRequest(Long id, MrmsPrincipal me) {
        return repository.findById(id)
                .filter(r -> r.getDispensaryId().equals(me.dispensaryId()))
                .orElseThrow(() -> new NotFoundException("Certificate"));
    }

    private static MrmsPrincipal requireRole(Role role) {
        MrmsPrincipal me = CurrentUser.get();
        if (me.role() != role) {
            throw new ForbiddenException("This action is only for the " + role.label());
        }
        return me;
    }

    private static MrmsPrincipal requireDispensaryStaff() {
        MrmsPrincipal me = CurrentUser.get();
        if (me.role() != Role.PHARMACIST && me.role() != Role.MEDICAL_OFFICER) {
            throw new ForbiddenException("This queue is only for dispensary staff");
        }
        return me;
    }

    private static NacStatus stageOf(MrmsPrincipal me) {
        return me.role() == Role.PHARMACIST ? NacStatus.PENDING_PHARMACIST : NacStatus.PENDING_MEDICAL_OFFICER;
    }

    private int slaDays(NacStatus stage) {
        return stage == NacStatus.PENDING_PHARMACIST
                ? props.sla().pharmacistDays()
                : props.sla().medicalOfficerDays();
    }

    private boolean overdue(NacRequest r) {
        if (r.getStatus() != NacStatus.PENDING_PHARMACIST && r.getStatus() != NacStatus.PENDING_MEDICAL_OFFICER) {
            return false;
        }
        return r.getStageEnteredAt().plus(Duration.ofDays(slaDays(r.getStatus()))).isBefore(clock.instant());
    }

    private void publish(NacRequest r, String remarks) {
        events.publishEvent(new NacStatusChanged(r.getId(), r.getNacNumber(), r.getEmployeeUserId(),
                r.getDispensaryId(), r.getAssignedTo(), r.getStatus(), blankToNull(remarks)));
    }

    private static String decisionSummary(NacRequest r) {
        Map<Decision, Long> counts = r.getItems().stream()
                .filter(i -> i.getDecision() != null)
                .collect(Collectors.groupingBy(NacItem::getDecision, Collectors.counting()));
        return counts.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private static NacItemRef ref(NacRequest r, NacItem i) {
        return new NacItemRef(i.getId(), r.getId(), r.getNacNumber(), r.getStatus(), r.getEmployeeUserId(),
                r.getPatientName(), r.getPatientRelation(), r.getDependentId(), i.getItemName(), i.getItemType(),
                i.getQuantity(), i.getDecision(), i.getDecisionReason(), r.getDispensaryId(),
                r.getPrescriptionDocumentId(), r.getPrescriptionDate(), r.getPrescribedBy(), r.getIssuedAt());
    }

    private static Map<String, Long> toCounts(List<Object[]> rows) {
        Map<String, Long> counts = new HashMap<>();
        for (NacStatus s : NacStatus.values()) {
            counts.put(s.name(), 0L);
        }
        rows.forEach(row -> counts.put(row[0].toString(), ((Number) row[1]).longValue()));
        return counts;
    }

    private List<NacSummary> summaries(List<NacRequest> requests) {
        Map<Long, String> names = accounts.fullNames(requests.stream()
                .flatMap(r -> Stream.of(r.getEmployeeUserId(), r.getAssignedTo()))
                .filter(Objects::nonNull).toList());
        Map<Long, String> dispensaries = organisation.dispensaryNames(
                requests.stream().map(NacRequest::getDispensaryId).collect(Collectors.toSet()));
        return requests.stream().map(r -> new NacSummary(r.getId(), r.getNacNumber(), r.getStatus(),
                names.get(r.getEmployeeUserId()), r.getPatientName(), r.getPatientRelation(),
                dispensaries.get(r.getDispensaryId()), r.getPrescriptionDate(), r.getItems().size(),
                r.getCreatedAt(), r.getQueueSince(), r.getStageEnteredAt(), overdue(r),
                r.getAssignedTo() == null ? null : names.get(r.getAssignedTo()))).toList();
    }

    private NacView view(NacRequest r) {
        MrmsPrincipal me = CurrentUser.get();
        List<Long> people = new ArrayList<>(List.of(r.getEmployeeUserId()));
        Stream.of(r.getAssignedTo(), r.getPharmacistId(), r.getMedicalOfficerId())
                .filter(Objects::nonNull).forEach(people::add);
        r.getItems().stream().map(NacItem::getDecidedBy).filter(Objects::nonNull).forEach(people::add);
        Map<Long, String> names = accounts.fullNames(people);

        Integer position = null;
        if (r.getStatus() == NacStatus.PENDING_PHARMACIST || r.getStatus() == NacStatus.PENDING_MEDICAL_OFFICER) {
            List<NacRequest> queue = repository.findByDispensaryIdAndStatusOrderByQueueSinceAscIdAsc(
                    r.getDispensaryId(), r.getStatus());
            for (int i = 0; i < queue.size(); i++) {
                if (queue.get(i).getId().equals(r.getId())) {
                    position = i + 1;
                    break;
                }
            }
        }
        List<ItemView> items = r.getItems().stream().map(i -> new ItemView(i.getId(), i.getLineNo(),
                i.getItemName(), i.getItemType(), i.getQuantity(), i.getDecision(), i.getDecisionReason(),
                i.getDecidedBy() == null ? null : names.get(i.getDecidedBy()), i.getDecidedAt())).toList();
        return new NacView(r.getId(), r.getNacNumber(), r.getStatus(), r.getEmployeeUserId(),
                names.get(r.getEmployeeUserId()), r.getDispensaryId(),
                organisation.dispensary(r.getDispensaryId()).map(OfficeRef::name).orElse(null),
                r.getPatientName(), r.getPatientRelation(), r.getPrescriptionDate(), r.getPrescribedBy(),
                documents.meta(r.getPrescriptionDocumentId()).orElse(null), items, r.getCreatedAt(),
                r.getQueueSince(), r.getStageEnteredAt(), position, overdue(r),
                r.getAssignedTo() == null ? null : names.get(r.getAssignedTo()),
                me.userId().equals(r.getAssignedTo()),
                r.getPharmacistId() == null ? null : names.get(r.getPharmacistId()), r.getPharmacistAt(),
                r.getPharmacistRemarks(),
                r.getMedicalOfficerId() == null ? null : names.get(r.getMedicalOfficerId()), r.getMedicalOfficerAt(),
                r.getMedicalOfficerRemarks(), r.getReturnRemarks(), r.getIssuedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
