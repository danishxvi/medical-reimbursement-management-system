/**
 * Claim module: the medical reimbursement claim and its workflow.
 *
 * <pre>
 * DRAFT -> PENDING_HOS -> PENDING_PAO_AUDIT -> PENDING_SANCTION -> SANCTIONED -> PAID
 *              |                 |                    |
 *              v                 v                    v
 *       RETURNED_BY_HOS   RETURNED_BY_PAO         REJECTED
 * </pre>
 *
 * A returned claim is corrected (not refilled) and resubmitted to the Head
 * of School; it keeps its original submission time, so it goes straight to
 * the front of every queue it passes through again.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Claims and workflow")
package com.mrms.claim;
