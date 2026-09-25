package com.mrms.claim.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.claim.ClaimStatus;
import com.mrms.shared.domain.QueueSource;
import com.mrms.shared.domain.Role;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/** Claims waiting at the school or the PAO, for the time limit watch. */
@Component
class ClaimQueueSource implements QueueSource {

    private final ClaimRepository claims;
    private final ClaimSupport support;
    private final AuditTrail audit;
    private final Clock clock;

    ClaimQueueSource(ClaimRepository claims, ClaimSupport support, AuditTrail audit, Clock clock) {
        this.claims = claims;
        this.support = support;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Item> openItems() {
        return claims.findByStatusIn(EnumSet.of(ClaimStatus.PENDING_HOS, ClaimStatus.PENDING_PAO_AUDIT,
                        ClaimStatus.PENDING_SANCTION)).stream()
                .filter(c -> c.getStageEnteredAt() != null)
                .map(c -> {
                    boolean school = c.getStatus() == ClaimStatus.PENDING_HOS;
                    Role official = switch (c.getStatus()) {
                        case PENDING_HOS -> Role.HOS;
                        case PENDING_PAO_AUDIT -> Role.PAO_AUDITOR;
                        default -> Role.PAO_OFFICER;
                    };
                    // An overdue scrutiny is reported to the PAO officer first; the others go to the zone
                    Role supervisor = c.getStatus() == ClaimStatus.PENDING_PAO_AUDIT ? Role.PAO_OFFICER : null;
                    return new Item("CLAIM", c.getId(), c.getClaimNumber(), c.getStatus().name(),
                            c.getStatus().label(), c.getStageEnteredAt(), support.slaDays(c.getStatus()),
                            c.getAssignedTo(), school ? "SCHOOL" : "PAO", school ? c.getSchoolId() : c.getPaoId(),
                            official, supervisor, c.getEmployeeUserId(), c.getSchoolId());
                })
                .toList();
    }

    @Override
    @Transactional
    public boolean releaseToQueue(Long subjectId, String stage, Instant stageEnteredAt) {
        return claims.findById(subjectId).map(c -> {
            boolean released = c.releaseOverdue(ClaimStatus.valueOf(stage), stageEnteredAt, clock.instant());
            if (released) {
                audit.record("CLAIM_AUTO_RELEASED", "CLAIM", c.getId(),
                        c.getClaimNumber() + ": time limit passed while held, back in the queue");
            }
            return released;
        }).orElse(false);
    }
}
