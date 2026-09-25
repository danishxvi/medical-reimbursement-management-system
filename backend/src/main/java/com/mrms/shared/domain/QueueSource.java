package com.mrms.shared.domain;

import java.time.Instant;
import java.util.List;

/**
 * Implemented by every module that has work queues with time limits (claims,
 * e-NAC). The escalation module watches all of them through this interface
 * without depending on the modules themselves.
 */
public interface QueueSource {

    /** Records currently waiting in a queue stage. */
    List<Item> openItems();

    /**
     * Puts a record back in its queue if it is still held in the same stage
     * that started at {@code stageEnteredAt}, so a colleague can take it.
     *
     * @return true if it was released
     */
    boolean releaseToQueue(Long subjectId, String stage, Instant stageEnteredAt);

    /**
     * @param subjectType    CLAIM or NAC
     * @param reference      what people call the record, for example the claim number
     * @param officeType     SCHOOL, PAO or DISPENSARY
     * @param officialRole   role that works this queue
     * @param supervisorRole role told first when the time limit passes (null if none in the office)
     * @param schoolId       school of the employee, which decides the education zone
     */
    record Item(String subjectType, Long subjectId, String reference, String stage, String stageLabel,
                Instant stageEnteredAt, int slaDays, Long assignedTo, String officeType, Long officeId,
                Role officialRole, Role supervisorRole, Long employeeUserId, Long schoolId) {
    }
}
