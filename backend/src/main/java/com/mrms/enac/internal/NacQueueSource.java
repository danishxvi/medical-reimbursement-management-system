package com.mrms.enac.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.enac.NacTypes.NacStatus;
import com.mrms.organisation.OrganisationDirectory;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.domain.QueueSource;
import com.mrms.shared.domain.Role;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/** Prescriptions waiting at the dispensary, for the time limit watch. */
@Component
class NacQueueSource implements QueueSource {

    private final NacRepository repository;
    private final OrganisationDirectory organisation;
    private final AuditTrail audit;
    private final MrmsProperties props;
    private final Clock clock;

    NacQueueSource(NacRepository repository, OrganisationDirectory organisation, AuditTrail audit,
                   MrmsProperties props, Clock clock) {
        this.repository = repository;
        this.organisation = organisation;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Item> openItems() {
        return repository.findByStatusIn(EnumSet.of(NacStatus.PENDING_PHARMACIST, NacStatus.PENDING_MEDICAL_OFFICER))
                .stream()
                .map(r -> {
                    boolean pharmacist = r.getStatus() == NacStatus.PENDING_PHARMACIST;
                    Long schoolId = organisation.profileOf(r.getEmployeeUserId())
                            .map(p -> p.school() == null ? null : p.school().id()).orElse(null);
                    return new Item("NAC", r.getId(), "e-NAC request " + r.getId(), r.getStatus().name(),
                            pharmacist ? "With pharmacist" : "With Medical Officer", r.getStageEnteredAt(),
                            pharmacist ? props.sla().pharmacistDays() : props.sla().medicalOfficerDays(),
                            r.getAssignedTo(), "DISPENSARY", r.getDispensaryId(),
                            pharmacist ? Role.PHARMACIST : Role.MEDICAL_OFFICER,
                            // The Medical Officer supervises the pharmacists of the dispensary
                            pharmacist ? Role.MEDICAL_OFFICER : null,
                            r.getEmployeeUserId(), schoolId);
                })
                .toList();
    }

    @Override
    @Transactional
    public boolean releaseToQueue(Long subjectId, String stage, Instant stageEnteredAt) {
        return repository.findById(subjectId).map(r -> {
            boolean released = r.releaseOverdue(NacStatus.valueOf(stage), stageEnteredAt, clock.instant());
            if (released) {
                audit.record("NAC_AUTO_RELEASED", "NAC", r.getId(), "Time limit passed while held, back in the queue");
            }
            return released;
        }).orElse(false);
    }
}
