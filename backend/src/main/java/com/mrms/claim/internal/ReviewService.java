package com.mrms.claim.internal;

import com.mrms.claim.ClaimStatus;
import com.mrms.claim.internal.ClaimDtos.Admission;
import com.mrms.claim.internal.ClaimDtos.AuditRequest;
import com.mrms.claim.internal.ClaimDtos.ClaimSummary;
import com.mrms.claim.internal.ClaimDtos.ClaimView;
import com.mrms.claim.internal.ClaimDtos.HosForwardRequest;
import com.mrms.claim.internal.ClaimDtos.QueueView;
import com.mrms.claim.internal.ClaimDtos.Restriction;
import com.mrms.claim.internal.ClaimDtos.ReturnRequest;
import com.mrms.claim.internal.ClaimEnums.Recommendation;
import com.mrms.esign.SignableAction;
import com.mrms.esign.Signatures;
import com.mrms.identity.Accounts;
import com.mrms.organisation.OrganisationDirectory;
import com.mrms.rates.RateBasis;
import com.mrms.rates.RateLookup;
import com.mrms.shared.domain.Role;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.ForbiddenException;
import com.mrms.shared.web.NotFoundException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reviewer side of the claim workflow: Head of School, PAO auditor (maker)
 * and PAO officer (checker). Every reviewer works through a strict first
 * come first served queue.
 */
@Service
class ReviewService {

    private static final int LIST_LIMIT = 200;

    private final ClaimRepository claims;
    private final ClaimSupport support;
    private final Accounts accounts;
    private final OrganisationDirectory organisation;
    private final Signatures signatures;

    ReviewService(ClaimRepository claims, ClaimSupport support, Accounts accounts,
                  OrganisationDirectory organisation, Signatures signatures) {
        this.claims = claims;
        this.support = support;
        this.accounts = accounts;
        this.organisation = organisation;
        this.signatures = signatures;
    }

    // ==================================================================
    // Signing: what exactly each signature covers
    // ==================================================================

    static final String HOS_CERTIFY = "CLAIM_HOS_CERTIFY";
    static final String SANCTION = "CLAIM_SANCTION";
    static final String REJECT = "CLAIM_REJECT";

    /**
     * The HoS certificate covers the claim as it stands, the rate basis,
     * every restriction and remark entered on the calculation sheet, and
     * the certificate text itself.
     */
    private String hosDigest(Claim claim, HosForwardRequest body) {
        StringBuilder s = new StringBuilder(HOS_CERTIFY).append('|')
                .append(ClaimFingerprint.of(support.view(claim, List.of())))
                .append("|basis=").append(body.rateBasis() != null ? body.rateBasis() : claim.suggestedRateBasis());
        body.items().stream().sorted(java.util.Comparator.comparing(Restriction::itemId))
                .forEach(r -> s.append("|item=").append(r.itemId()).append(',').append(plain(r.dgehsRate()))
                        .append(',').append(plain(r.amountRestricted())).append(',').append(nz(r.remarks())));
        s.append("|remarks=").append(nz(body.remarks()))
                .append("|certificate=").append(String.join("\n", ClaimTexts.HOS_CERTIFICATE));
        return ClaimFingerprint.sha256(s.toString());
    }

    private String decisionDigest(String purpose, Claim claim, String text) {
        return ClaimFingerprint.sha256(purpose + '|' + ClaimFingerprint.of(support.view(claim, List.of()))
                + "|text=" + nz(text));
    }

    /** Called before an eSign starts: may this user sign, and what will they sign. */
    @Transactional(readOnly = true)
    SignableAction.Signable describeHosCertify(Long id, HosForwardRequest body) {
        MrmsPrincipal me = requireRole(Role.HOS);
        Claim claim = heldClaim(id, me, ClaimStatus.PENDING_HOS);
        BigDecimal total = body.items().stream().map(Restriction::amountRestricted)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SignableAction.Signable(hosDigest(claim, body),
                "Head of School certificate for claim " + claim.getClaimNumber() + ", restricted total Rs. " + total);
    }

    @Transactional(readOnly = true)
    SignableAction.Signable describeDecision(String purpose, Long id, String text) {
        MrmsPrincipal me = requireRole(Role.PAO_OFFICER);
        Claim claim = heldClaim(id, me, ClaimStatus.PENDING_SANCTION);
        String what = SANCTION.equals(purpose) ? "Sanction of claim " + claim.getClaimNumber() + ", Rs. "
                + claim.getAdmittedAmount() : "Rejection of claim " + claim.getClaimNumber();
        return new SignableAction.Signable(decisionDigest(purpose, claim, text), what);
    }

    private Claim heldClaim(Long id, MrmsPrincipal me, ClaimStatus stage) {
        Claim claim = scopedClaim(id, me);
        if (claim.getStatus() != stage || !me.userId().equals(claim.getAssignedTo())) {
            throw new BusinessRuleException("NOT_ASSIGNED", "Take this claim from your queue before signing");
        }
        return claim;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    // ==================================================================
    // Queue
    // ==================================================================

    @Transactional(readOnly = true)
    QueueView queue() {
        MrmsPrincipal me = requireReviewer();
        ClaimStatus stage = stageOf(me);
        List<Claim> waiting = queueOf(me, stage);
        ClaimView current = waiting.stream()
                .filter(c -> me.userId().equals(c.getAssignedTo()))
                .findFirst()
                .map(c -> support.view(c, ClaimService.allowedActions(me, c)))
                .orElse(null);
        return new QueueView(stage, stage.label(), support.slaDays(stage), current, support.summaries(waiting));
    }

    /**
     * Gives the reviewer the claim they already hold, or else the oldest
     * untaken claim. Reviewers cannot pick claims, which removes any room
     * for favouritism.
     */
    @Transactional
    ClaimView takeNext() {
        MrmsPrincipal me = requireReviewer();
        ClaimStatus stage = stageOf(me);
        Optional<Claim> held = claims.findByStatusAndAssignedTo(stage, me.userId()).stream()
                .filter(c -> inScope(me, c)).findFirst();
        if (held.isPresent()) {
            return support.view(held.get(), ClaimService.allowedActions(me, held.get()));
        }
        List<Claim> head = me.role() == Role.HOS
                ? claims.findBySchoolIdAndStatusAndAssignedToIsNullOrderByFirstSubmittedAtAscIdAsc(
                me.schoolId(), stage, Limit.of(1))
                : claims.findByPaoIdAndStatusAndAssignedToIsNullOrderByFirstSubmittedAtAscIdAsc(
                me.paoId(), stage, Limit.of(1));
        Claim next = head.stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("QUEUE_EMPTY", "There is nothing waiting in your queue"));
        next.take(me.userId(), support.now());
        claims.saveAndFlush(next);
        support.auditOnly(next, "TAKEN");
        return support.view(next, ClaimService.allowedActions(me, next));
    }

    @Transactional
    void release(Long id) {
        MrmsPrincipal me = requireReviewer();
        Claim claim = scopedClaim(id, me);
        claim.release(me.userId(), support.now());
        support.auditOnly(claim, "RELEASED");
    }

    /** All claims of the reviewer's office (history view), newest first. */
    @Transactional(readOnly = true)
    List<ClaimSummary> officeClaims(ClaimStatus status) {
        MrmsPrincipal me = requireReviewer();
        List<Claim> list = me.role() == Role.HOS
                ? claims.findForSchool(me.schoolId(), status, Limit.of(LIST_LIMIT))
                : claims.findForPao(me.paoId(), status, Limit.of(LIST_LIMIT));
        return support.summaries(list);
    }

    // ==================================================================
    // Head of School
    // ==================================================================

    @Transactional
    ClaimView hosForward(Long id, HosForwardRequest body) {
        MrmsPrincipal me = requireRole(Role.HOS);
        Claim claim = scopedClaim(id, me);
        // Signed before anything changes, over the content being certified
        signatures.confirm(new Signatures.StepUp(body.password(), body.esignTxn()), HOS_CERTIFY, "CLAIM",
                id.toString(), hosDigest(claim, body));

        RateBasis basis = body.rateBasis() != null ? body.rateBasis() : claim.suggestedRateBasis();
        String ward = organisation.profileOf(claim.getEmployeeUserId())
                .map(p -> p.wardEntitlement()).orElse(null);
        boolean indoor = claim.getTreatmentType() == ClaimEnums.TreatmentType.INDOOR;

        Map<Long, ClaimItem> items = itemsById(claim);
        for (Restriction r : body.items()) {
            ClaimItem item = items.get(r.itemId());
            if (item == null) {
                throw new BusinessRuleException("UNKNOWN_ITEM", "An item does not belong to this claim");
            }
            String reference = support.quote(item, basis, ward, indoor)
                    .map(RateLookup.RateQuote::reference).orElse(null);
            if (r.amountRestricted().compareTo(item.getAmountClaimed()) > 0) {
                throw new BusinessRuleException("OVER_CLAIM",
                        "Restricted amount cannot exceed the amount claimed for \"" + item.getDescription() + "\"");
            }
            if (r.amountRestricted().compareTo(item.getAmountClaimed()) < 0 && isBlank(r.remarks())) {
                throw new BusinessRuleException("REMARKS_REQUIRED",
                        "Give a remark for every item restricted below the claimed amount");
            }
            item.restrict(r.dgehsRate(), r.amountRestricted(), ClaimSupport.blankToNull(r.remarks()), reference);
        }
        ClaimStatus from = claim.forwardToPao(me.userId(), basis, support.now());
        claims.saveAndFlush(claim);
        support.recordTransition(claim, "FORWARDED_BY_HOS", from, body.remarks(), null);
        return support.view(claim, ClaimService.allowedActions(me, claim));
    }

    @Transactional
    ClaimView hosReturn(Long id, ReturnRequest body) {
        MrmsPrincipal me = requireRole(Role.HOS);
        Claim claim = scopedClaim(id, me);
        ClaimStatus from = claim.returnByHos(me.userId(), support.now());
        claims.saveAndFlush(claim);
        support.recordTransition(claim, "RETURNED_BY_HOS", from, body.remarks(), body.reasons());
        return support.view(claim, ClaimService.allowedActions(me, claim));
    }

    // ==================================================================
    // PAO auditor
    // ==================================================================

    @Transactional
    ClaimView audit(Long id, AuditRequest body) {
        MrmsPrincipal me = requireRole(Role.PAO_AUDITOR);
        Claim claim = scopedClaim(id, me);
        if (body.recommendation() == Recommendation.REJECT && isBlank(body.remarks())) {
            throw new BusinessRuleException("REMARKS_REQUIRED", "Explain why the claim should be rejected");
        }
        Map<Long, ClaimItem> items = itemsById(claim);
        for (Admission a : body.items()) {
            ClaimItem item = items.get(a.itemId());
            if (item == null) {
                throw new BusinessRuleException("UNKNOWN_ITEM", "An item does not belong to this claim");
            }
            BigDecimal ceiling = item.getAmountRestricted() != null ? item.getAmountRestricted() : item.getAmountClaimed();
            if (a.amountAdmitted().compareTo(ceiling) > 0) {
                throw new BusinessRuleException("OVER_RESTRICTED",
                        "Admitted amount cannot exceed the school restricted amount for \"" + item.getDescription() + "\"");
            }
            if (a.amountAdmitted().compareTo(ceiling) < 0 && isBlank(a.disallowReason())) {
                throw new BusinessRuleException("REASON_REQUIRED",
                        "Give a reason for every amount disallowed (\"" + item.getDescription() + "\")");
            }
            item.admit(a.amountAdmitted(), ClaimSupport.blankToNull(a.disallowReason()));
        }
        ClaimStatus from = claim.recommend(me.userId(), body.recommendation(), support.now());
        claims.saveAndFlush(claim);
        support.recordTransition(claim, body.recommendation() == Recommendation.SANCTION
                ? "RECOMMENDED_FOR_SANCTION" : "RECOMMENDED_FOR_REJECTION", from, body.remarks(), null);
        return support.view(claim, ClaimService.allowedActions(me, claim));
    }

    @Transactional
    ClaimView paoReturn(Long id, ReturnRequest body) {
        MrmsPrincipal me = requireRole(Role.PAO_AUDITOR);
        Claim claim = scopedClaim(id, me);
        ClaimStatus from = claim.returnByPao(me.userId(), support.now());
        claims.saveAndFlush(claim);
        support.recordTransition(claim, "RETURNED_BY_PAO", from, body.remarks(), body.reasons());
        return support.view(claim, ClaimService.allowedActions(me, claim));
    }

    // ==================================================================
    // PAO officer
    // ==================================================================

    @Transactional
    ClaimView sanction(Long id, Signatures.StepUp stepUp, String remarks) {
        MrmsPrincipal me = requireRole(Role.PAO_OFFICER);
        Claim claim = scopedClaim(id, me);
        signatures.confirm(stepUp, SANCTION, "CLAIM", id.toString(), decisionDigest(SANCTION, claim, remarks));
        ClaimStatus from = claim.sanction(me.userId(), support.now());
        claims.saveAndFlush(claim);
        support.recordTransition(claim, "SANCTIONED", from, remarks, null);
        return support.view(claim, ClaimService.allowedActions(me, claim));
    }

    @Transactional
    ClaimView sendBack(Long id, String remarks) {
        MrmsPrincipal me = requireRole(Role.PAO_OFFICER);
        Claim claim = scopedClaim(id, me);
        ClaimStatus from = claim.sendBackToAudit(me.userId(), support.now());
        claims.saveAndFlush(claim);
        support.recordTransition(claim, "SENT_BACK_TO_AUDIT", from, remarks, null);
        return support.view(claim, ClaimService.allowedActions(me, claim));
    }

    @Transactional
    ClaimView reject(Long id, Signatures.StepUp stepUp, String reason) {
        MrmsPrincipal me = requireRole(Role.PAO_OFFICER);
        Claim claim = scopedClaim(id, me);
        signatures.confirm(stepUp, REJECT, "CLAIM", id.toString(), decisionDigest(REJECT, claim, reason));
        ClaimStatus from = claim.reject(me.userId(), reason.trim(), support.now());
        claims.saveAndFlush(claim);
        support.recordTransition(claim, "REJECTED", from, reason, null);
        return support.view(claim, ClaimService.allowedActions(me, claim));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private List<Claim> queueOf(MrmsPrincipal me, ClaimStatus stage) {
        return me.role() == Role.HOS
                ? claims.findBySchoolIdAndStatusOrderByFirstSubmittedAtAscIdAsc(me.schoolId(), stage)
                : claims.findByPaoIdAndStatusOrderByFirstSubmittedAtAscIdAsc(me.paoId(), stage);
    }

    private Claim scopedClaim(Long id, MrmsPrincipal me) {
        return claims.findById(id).filter(c -> inScope(me, c))
                .orElseThrow(() -> new NotFoundException("Claim"));
    }

    private static boolean inScope(MrmsPrincipal me, Claim c) {
        return me.role() == Role.HOS ? c.getSchoolId().equals(me.schoolId()) : c.getPaoId().equals(me.paoId());
    }

    private static Map<Long, ClaimItem> itemsById(Claim claim) {
        return claim.getItems().stream().collect(Collectors.toMap(ClaimItem::getId, Function.identity()));
    }

    private static ClaimStatus stageOf(MrmsPrincipal me) {
        return switch (me.role()) {
            case HOS -> ClaimStatus.PENDING_HOS;
            case PAO_AUDITOR -> ClaimStatus.PENDING_PAO_AUDIT;
            case PAO_OFFICER -> ClaimStatus.PENDING_SANCTION;
            default -> throw new ForbiddenException("No review queue for this role");
        };
    }

    private static MrmsPrincipal requireReviewer() {
        MrmsPrincipal me = CurrentUser.get();
        stageOf(me);
        return me;
    }

    private static MrmsPrincipal requireRole(Role role) {
        MrmsPrincipal me = CurrentUser.get();
        if (me.role() != role) {
            throw new ForbiddenException("This action is only for the " + role.label());
        }
        return me;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
