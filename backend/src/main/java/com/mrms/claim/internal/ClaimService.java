package com.mrms.claim.internal;

import com.mrms.claim.ClaimStatus;
import com.mrms.claim.internal.Claim.ClaimContent;
import com.mrms.claim.internal.ClaimDtos.AttachmentInput;
import com.mrms.claim.internal.ClaimDtos.ClaimInput;
import com.mrms.claim.internal.ClaimDtos.ClaimMeta;
import com.mrms.claim.internal.ClaimDtos.ClaimSummary;
import com.mrms.claim.internal.ClaimDtos.ClaimView;
import com.mrms.claim.internal.ClaimDtos.ClaimableNacItem;
import com.mrms.claim.internal.ClaimDtos.ItemInput;
import com.mrms.claim.internal.ClaimDtos.Option;
import com.mrms.claim.internal.ClaimEnums.HospitalType;
import com.mrms.claim.internal.ClaimEnums.ItemCategory;
import com.mrms.claim.internal.ClaimEnums.ReturnReason;
import com.mrms.claim.internal.ClaimEnums.TreatmentType;
import com.mrms.document.DocumentCategory;
import com.mrms.document.DocumentMeta;
import com.mrms.document.DocumentStore;
import com.mrms.enac.NacItemRef;
import com.mrms.enac.NacLookup;
import com.mrms.organisation.OrganisationDirectory;
import com.mrms.organisation.OrganisationViews.DependentView;
import com.mrms.organisation.OrganisationViews.EmployeeProfileView;
import com.mrms.organisation.OrganisationViews.SchoolRef;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.domain.FinancialYear;
import com.mrms.shared.domain.Relation;
import com.mrms.shared.domain.Role;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.ForbiddenException;
import com.mrms.shared.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Employee side of the claim workflow. */
@Service
class ClaimService {

    private final ClaimRepository claims;
    private final ClaimSupport support;
    private final DocumentStore documents;
    private final NacLookup nac;
    private final OrganisationDirectory organisation;
    private final MrmsProperties props;

    ClaimService(ClaimRepository claims, ClaimSupport support, DocumentStore documents, NacLookup nac,
                 OrganisationDirectory organisation, MrmsProperties props) {
        this.claims = claims;
        this.support = support;
        this.documents = documents;
        this.nac = nac;
        this.organisation = organisation;
        this.props = props;
    }

    // ==================================================================
    // Reference data
    // ==================================================================

    ClaimMeta meta() {
        return new ClaimMeta(
                Arrays.stream(TreatmentType.values()).map(t -> new Option(t.name(), t.label)).toList(),
                Arrays.stream(HospitalType.values()).map(t -> new Option(t.name(), t.label)).toList(),
                Arrays.stream(ItemCategory.values()).map(t -> new Option(t.name(), t.label)).toList(),
                Arrays.stream(ReturnReason.values()).map(t -> new Option(t.name(), t.label)).toList(),
                ClaimTexts.attachmentCategoriesAllowed().stream().map(t -> new Option(t.name(), t.label())).toList(),
                ClaimTexts.UNDERTAKING,
                ClaimTexts.HOS_CERTIFICATE,
                props.claim().submissionWindowDays(),
                props.claim().maxItems());
    }

    // ==================================================================
    // Queries
    // ==================================================================

    @Transactional(readOnly = true)
    List<ClaimSummary> mine() {
        MrmsPrincipal me = requireEmployee();
        return support.summaries(claims.findByEmployeeUserIdOrderByCreatedAtDesc(me.userId()));
    }

    /** e-NAC items the employee can still claim (not already in another active claim). */
    @Transactional(readOnly = true)
    List<ClaimableNacItem> claimableNacItems() {
        MrmsPrincipal me = requireEmployee();
        Set<Long> used = new HashSet<>(claims.usedNacItemIds(me.userId(), ClaimStatus.ACTIVE));
        return nac.claimableItems(me.userId()).stream()
                .filter(i -> !used.contains(i.itemId()))
                .map(i -> new ClaimableNacItem(i.itemId(), i.nacRequestId(), i.nacNumber(), i.itemName(),
                        i.quantity(), i.patientName(), i.patientRelation(), i.dependentId(), i.prescriptionDate(),
                        i.issuedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    ClaimView get(Long id) {
        Claim claim = support.visibleClaim(id);
        return support.view(claim, allowedActions(CurrentUser.get(), claim));
    }

    /** The complete claim as a printable PDF, for anyone who may see the claim. */
    @Transactional
    PrintedClaim pdf(Long id) {
        Claim claim = support.visibleClaim(id);
        ClaimView view = support.view(claim, List.of());
        String fingerprint = ClaimFingerprint.of(view);
        Map<UUID, DocumentMeta> metas = documents.metas(view.documentNames().keySet()).stream()
                .collect(Collectors.toMap(DocumentMeta::id, Function.identity()));
        byte[] pdf = ClaimPdf.render(new ClaimPdf.Input(view, claim.getUndertakingAcceptedAt(),
                ClaimTexts.UNDERTAKING, ClaimTexts.HOS_CERTIFICATE, metas, support.signatureLines(claim),
                fingerprint, support.now(), CurrentUser.get().fullName()));
        support.auditOnly(claim, "PDF_DOWNLOADED");
        String name = (claim.getClaimNumber() == null ? "DRAFT-" + claim.getId() : claim.getClaimNumber().replace('/', '-'))
                + ".pdf";
        return new PrintedClaim(name, pdf);
    }

    record PrintedClaim(String fileName, byte[] content) {
    }

    @Transactional
    NamedContent document(Long claimId, UUID documentId) {
        Claim claim = support.visibleClaim(claimId);
        if (!support.documentIdsOf(claim).contains(documentId)) {
            throw new NotFoundException("Document");
        }
        String name = support.documentNames(claim).get(documentId);
        DocumentStore.DocumentContent content = documents.read(documentId);
        return new NamedContent(content, name == null ? content.meta().standardName() : name);
    }

    /** A claim document together with its standard name inside the claim. */
    record NamedContent(DocumentStore.DocumentContent content, String fileName) {

        org.springframework.http.ResponseEntity<byte[]> toResponse() {
            return content.toResponse(fileName);
        }
    }

    // ==================================================================
    // Draft handling
    // ==================================================================

    @Transactional
    ClaimView createDraft(ClaimInput input) {
        MrmsPrincipal me = requireEmployee();
        SchoolRef school = organisation.school(me.schoolId())
                .orElseThrow(() -> new BusinessRuleException("NO_SCHOOL", "Your account is not linked to a school"));
        Claim claim = new Claim(me.userId(), school.id(), school.paoId(), support.now());
        claim.updateContent(assemble(me, input), support.now());
        claims.save(claim);
        return support.view(claim, allowedActions(me, claim));
    }

    @Transactional
    ClaimView update(Long id, ClaimInput input) {
        MrmsPrincipal me = requireEmployee();
        Claim claim = ownClaim(id, me);
        claim.updateContent(assemble(me, input), support.now());
        claims.saveAndFlush(claim);
        return support.view(claim, allowedActions(me, claim));
    }

    @Transactional
    void deleteDraft(Long id) {
        MrmsPrincipal me = requireEmployee();
        Claim claim = ownClaim(id, me);
        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw new BusinessRuleException("NOT_DRAFT", "Only drafts can be deleted. Withdraw the claim instead");
        }
        claims.delete(claim);
    }

    // ==================================================================
    // Submission
    // ==================================================================

    @Transactional
    ClaimView submit(Long id) {
        MrmsPrincipal me = requireEmployee();
        Claim claim = ownClaim(id, me);
        validateForSubmission(me, claim);

        boolean first = claim.getFirstSubmittedAt() == null;
        String number = null;
        String fy = FinancialYear.current();
        if (first) {
            String schoolCode = organisation.school(claim.getSchoolId()).map(SchoolRef::code).orElse("NA");
            number = "MR/" + schoolCode + "/" + fy + "/" + String.format("%06d", claims.nextNumber());
        }
        ClaimStatus from = claim.submit(me.userId(), number, fy, support.now());
        claims.saveAndFlush(claim);
        support.recordTransition(claim, first ? "SUBMITTED" : "RESUBMITTED", from, null, null);
        return support.view(claim, allowedActions(me, claim));
    }

    @Transactional
    ClaimView withdraw(Long id) {
        MrmsPrincipal me = requireEmployee();
        Claim claim = ownClaim(id, me);
        ClaimStatus from = claim.withdraw(me.userId(), support.now());
        claims.saveAndFlush(claim);
        support.recordTransition(claim, "WITHDRAWN", from, null, null);
        return support.view(claim, allowedActions(me, claim));
    }

    /**
     * All rules a claim must satisfy before it leaves the employee. Checking
     * everything here is what stops claims from being returned later for
     * simple mistakes.
     */
    private void validateForSubmission(MrmsPrincipal me, Claim claim) {
        List<String> problems = new ArrayList<>();
        LocalDate today = LocalDate.now(FinancialYear.IST);
        List<ClaimItem> items = claim.getItems();

        if (items.isEmpty()) {
            problems.add("Add at least one bill");
        }
        if (items.size() > props.claim().maxItems()) {
            problems.add("A claim can have at most " + props.claim().maxItems() + " bills");
        }
        if (claim.getTreatmentTo().isAfter(today)) {
            problems.add("Treatment end date cannot be in the future");
        }
        // Time limit applies to the first submission only; a returned claim was filed in time
        if (claim.getFirstSubmittedAt() == null
                && claim.getTreatmentTo().plusDays(props.claim().submissionWindowDays()).isBefore(today)) {
            problems.add("The claim must be submitted within " + props.claim().submissionWindowDays()
                    + " days of the end of treatment");
        }
        if (claim.getTreatmentType() == TreatmentType.INDOOR
                && (claim.getAdmissionDate() == null || claim.getDischargeDate() == null)) {
            problems.add("Enter the admission and discharge dates for indoor treatment");
        }

        // DGEHS card must be valid on the treatment dates (declaration in Annexure II)
        EmployeeProfileView profile = organisation.profileOf(me.userId()).orElse(null);
        if (profile == null || profile.dgehsCardNo() == null) {
            problems.add("Your DGEHS card details are missing. Ask your office to update your profile");
        } else if ((profile.dgehsValidFrom() != null && claim.getTreatmentFrom().isBefore(profile.dgehsValidFrom()))
                || (profile.dgehsValidTo() != null && claim.getTreatmentTo().isAfter(profile.dgehsValidTo()))) {
            problems.add("Your DGEHS card was not valid for the whole treatment period");
        }

        // Every bill date inside the treatment period
        for (ClaimItem i : items) {
            if (i.getBillDate().isBefore(claim.getTreatmentFrom()) || i.getBillDate().isAfter(claim.getTreatmentTo())) {
                problems.add("Bill " + i.getBillNumber() + " is dated outside the treatment period");
            }
        }

        // Medicines need an e-NAC item (or a scanned legacy NAC) for OPD treatment
        Set<DocumentCategory> attached = claim.getAttachments().stream().map(ClaimAttachment::getCategory)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DocumentCategory.class)));
        List<Long> nacIds = items.stream().map(ClaimItem::getNacItemId).filter(Objects::nonNull).toList();
        Map<Long, NacItemRef> nacItems = nac.items(nacIds).stream()
                .collect(Collectors.toMap(NacItemRef::itemId, Function.identity()));
        for (ClaimItem i : items) {
            if (i.getNacItemId() != null) {
                NacItemRef ref = nacItems.get(i.getNacItemId());
                if (ref == null || !ref.claimable() || !ref.employeeUserId().equals(me.userId())) {
                    problems.add("Item \"" + i.getDescription() + "\" is not covered by an issued e-NAC");
                } else if (!Objects.equals(ref.dependentId(), claim.getDependentId())) {
                    problems.add("The e-NAC for \"" + i.getDescription() + "\" is for a different patient");
                }
            } else if (i.getCategory() == ItemCategory.MEDICINE && claim.getTreatmentType() == TreatmentType.OPD) {
                if (!i.isLegacyNac()) {
                    problems.add("Link medicine \"" + i.getDescription() + "\" to its e-NAC item");
                } else if (!attached.contains(DocumentCategory.NAC_SCAN)) {
                    problems.add("Attach the scanned non availability certificate for legacy items");
                }
            }
        }
        if (new HashSet<>(nacIds).size() != nacIds.size()) {
            problems.add("The same e-NAC item is used twice in this claim");
        }

        // Required supporting documents
        if (!attached.contains(DocumentCategory.DGEHS_CARD)) {
            problems.add("Attach a copy of your DGEHS card");
        }
        if (claim.getTreatmentType() == TreatmentType.INDOOR && !attached.contains(DocumentCategory.DISCHARGE_SUMMARY)) {
            problems.add("Attach the discharge summary");
        }
        if (claim.getTreatmentType() == TreatmentType.OPD && nacIds.isEmpty()
                && !attached.contains(DocumentCategory.PRESCRIPTION)) {
            problems.add("Attach the prescription");
        }
        if (claim.isEmergency() && !attached.contains(DocumentCategory.EMERGENCY_CERTIFICATE)) {
            problems.add("Attach the emergency certificate from the hospital");
        }

        problems.addAll(duplicateProblems(me, claim, nacIds));

        if (!problems.isEmpty()) {
            throw new BusinessRuleException("CLAIM_INCOMPLETE", String.join(". ", problems));
        }
    }

    /** Stops the same bill or e-NAC item from being paid twice. */
    private List<String> duplicateProblems(MrmsPrincipal me, Claim claim, List<Long> nacIds) {
        List<String> problems = new ArrayList<>();
        Long claimId = claim.getId();
        if (!nacIds.isEmpty() && !claims.findActiveUsingNacItems(claimId, nacIds, ClaimStatus.ACTIVE).isEmpty()) {
            problems.add("An e-NAC item in this claim is already part of another claim");
        }

        // One bill may legitimately cover several items of this claim, so only other claims are checked
        List<UUID> billDocs = claim.getItems().stream().map(ClaimItem::getBillDocumentId).distinct().toList();
        if (!claims.findActiveUsingDocuments(claimId, billDocs, ClaimStatus.ACTIVE).isEmpty()) {
            problems.add("A bill file in this claim is already part of another claim");
        }
        // Same file content uploaded again under a new id
        for (DocumentMeta meta : documents.metas(billDocs)) {
            List<UUID> twins = documents.sameContent(meta.sha256()).stream()
                    .map(DocumentMeta::id).filter(x -> !x.equals(meta.id())).toList();
            if (!twins.isEmpty() && !claims.findActiveUsingDocuments(claimId, twins, ClaimStatus.ACTIVE).isEmpty()) {
                problems.add("The bill \"" + meta.originalName() + "\" matches a bill in another claim");
            }
        }
        for (ClaimItem i : claim.getItems()) {
            if (claims.countSameBill(claimId, me.userId(), i.getBillNumber(), i.getVendorName(), i.getBillDate(),
                    ClaimStatus.ACTIVE) > 0) {
                problems.add("Bill " + i.getBillNumber() + " of " + i.getVendorName() + " is already claimed");
            }
        }
        return problems;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Converts input into entities, checking ownership of every referenced record. */
    private ClaimContent assemble(MrmsPrincipal me, ClaimInput in) {
        if (in.treatmentTo().isBefore(in.treatmentFrom())) {
            throw new BusinessRuleException("INVALID_DATES", "Treatment end date is before the start date");
        }
        if (in.treatmentType() == TreatmentType.INDOOR && in.admissionDate() != null && in.dischargeDate() != null
                && in.dischargeDate().isBefore(in.admissionDate())) {
            throw new BusinessRuleException("INVALID_DATES", "Discharge date is before the admission date");
        }

        String patientName = me.fullName();
        Relation relation = Relation.SELF;
        if (in.dependentId() != null) {
            DependentView d = organisation.dependentOf(me.userId(), in.dependentId())
                    .orElseThrow(() -> new BusinessRuleException("UNKNOWN_DEPENDENT",
                            "Select a dependent from your profile"));
            patientName = d.fullName();
            relation = d.relation();
        }

        List<ItemInput> itemInputs = in.items() == null ? List.of() : in.items();
        List<AttachmentInput> attachmentInputs = in.attachments() == null ? List.of() : in.attachments();

        Set<UUID> allDocs = new HashSet<>();
        itemInputs.forEach(i -> allDocs.add(i.billDocumentId()));
        attachmentInputs.forEach(a -> allDocs.add(a.documentId()));
        Map<UUID, DocumentMeta> docs = documents.metas(allDocs).stream()
                .filter(m -> m.ownerUserId().equals(me.userId()))
                .collect(Collectors.toMap(DocumentMeta::id, Function.identity()));

        List<Long> nacIds = itemInputs.stream().map(ItemInput::nacItemId).filter(Objects::nonNull).toList();
        Set<Long> ownNacIds = nac.items(nacIds).stream()
                .filter(r -> r.employeeUserId().equals(me.userId()))
                .map(NacItemRef::itemId).collect(Collectors.toSet());

        List<ClaimItem> items = new ArrayList<>();
        int line = 1;
        for (ItemInput i : itemInputs) {
            DocumentMeta bill = docs.get(i.billDocumentId());
            if (bill == null || bill.category() != DocumentCategory.BILL) {
                throw new BusinessRuleException("UNKNOWN_DOCUMENT", "Upload the bill for \"" + i.description() + "\"");
            }
            if (i.nacItemId() != null && !ownNacIds.contains(i.nacItemId())) {
                throw new BusinessRuleException("UNKNOWN_NAC_ITEM", "Select an e-NAC item from your certificates");
            }
            items.add(new ClaimItem(line++, i.category(), i.description().trim(), i.billNumber().trim(),
                    i.billDate(), i.vendorName().trim(), ClaimSupport.blankToNull(i.dgehsCode()), i.amountClaimed(),
                    i.nacItemId(), i.nacItemId() == null && i.legacyNac(), i.billDocumentId()));
        }

        List<ClaimAttachment> attachments = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (AttachmentInput a : attachmentInputs) {
            DocumentMeta doc = docs.get(a.documentId());
            if (doc == null || doc.category() != a.category() || a.category() == DocumentCategory.BILL) {
                throw new BusinessRuleException("UNKNOWN_DOCUMENT", "An attached document is not valid");
            }
            if (seen.add(a.documentId())) {
                attachments.add(new ClaimAttachment(a.documentId(), a.category()));
            }
        }

        return new ClaimContent(patientName, relation, in.dependentId(), in.illnessDescription().trim(),
                in.treatmentType(), in.treatmentFrom(), in.treatmentTo(),
                in.treatmentType() == TreatmentType.INDOOR ? in.admissionDate() : null,
                in.treatmentType() == TreatmentType.INDOOR ? in.dischargeDate() : null,
                in.hospitalName().trim(), ClaimSupport.blankToNull(in.hospitalAddress()), in.hospitalType(),
                in.emergency(), ClaimSupport.blankToNull(in.referralDetails()),
                ClaimSupport.blankToNull(in.medicalAdvanceDetails()), items, attachments);
    }

    private Claim ownClaim(Long id, MrmsPrincipal me) {
        return claims.findById(id)
                .filter(c -> c.getEmployeeUserId().equals(me.userId()))
                .orElseThrow(() -> new NotFoundException("Claim"));
    }

    private static MrmsPrincipal requireEmployee() {
        MrmsPrincipal me = CurrentUser.get();
        if (me.role() != Role.EMPLOYEE) {
            throw new ForbiddenException("Only employees can file claims");
        }
        return me;
    }

    /** Buttons the client should offer; the server re-checks every action anyway. */
    static List<String> allowedActions(MrmsPrincipal me, Claim c) {
        List<String> actions = new ArrayList<>();
        boolean mine = me.userId().equals(c.getAssignedTo());
        switch (me.role()) {
            case EMPLOYEE -> {
                if (c.isEditable()) {
                    actions.add("EDIT");
                    actions.add("SUBMIT");
                }
                if (c.getStatus() == ClaimStatus.DRAFT) {
                    actions.add("DELETE");
                }
                if (c.getStatus() == ClaimStatus.RETURNED_BY_HOS || c.getStatus() == ClaimStatus.RETURNED_BY_PAO
                        || (c.getStatus() == ClaimStatus.PENDING_HOS && c.getAssignedTo() == null)) {
                    actions.add("WITHDRAW");
                }
            }
            case HOS -> {
                if (c.getStatus() == ClaimStatus.PENDING_HOS && mine) {
                    actions.addAll(List.of("FORWARD", "RETURN", "RELEASE"));
                }
            }
            case PAO_AUDITOR -> {
                if (c.getStatus() == ClaimStatus.PENDING_PAO_AUDIT && mine) {
                    actions.addAll(List.of("RECOMMEND", "RETURN", "RELEASE"));
                }
            }
            case PAO_OFFICER -> {
                if (c.getStatus() == ClaimStatus.PENDING_SANCTION && mine) {
                    actions.addAll(List.of("SANCTION", "SEND_BACK", "REJECT", "RELEASE"));
                }
            }
            default -> {
            }
        }
        return actions;
    }
}
