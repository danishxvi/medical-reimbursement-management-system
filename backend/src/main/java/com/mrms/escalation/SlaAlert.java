package com.mrms.escalation;

import java.util.List;

/**
 * Published when a record reaches a time limit step, or when an oversight
 * officer sends a reminder. The notification module turns it into messages.
 *
 * @param level          REMINDER (limit approaching), BREACH (limit passed), ESCALATED (sent to the zone)
 *                       or NUDGE (reminder sent by an oversight officer)
 * @param recipients     officials to tell
 * @param employeeUserId the employee whose record it is, told when the limit passes (null otherwise)
 * @param released       the record was taken from its holder and put back in the queue
 */
public record SlaAlert(String level, String subjectType, Long subjectId, String reference, String stageLabel,
                       int slaDays, List<Long> recipients, Long employeeUserId, boolean released) {
}
