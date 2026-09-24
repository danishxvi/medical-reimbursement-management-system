package com.mrms.audit;

/**
 * Public API of the audit module.
 *
 * <p>Entries are written in the caller's transaction, so an action and its
 * audit entry are committed or rolled back together.
 */
public interface AuditTrail {

    /**
     * Records an action performed by the logged in user.
     *
     * @param action     short upper case verb, for example CLAIM_SUBMITTED
     * @param entityType the kind of record affected, for example CLAIM
     * @param entityId   identifier of the record (may be null)
     * @param details    free text context; never put passwords or file content here
     */
    void record(String action, String entityType, Object entityId, String details);

    /**
     * Records an action where no user is logged in yet, such as a failed
     * login. The attempted username is stored for investigation.
     */
    void recordAnonymous(String attemptedUsername, String action, String details);
}
