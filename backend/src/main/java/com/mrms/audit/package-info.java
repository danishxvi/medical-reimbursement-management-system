/**
 * Audit module. Keeps an append only, hash chained record of every
 * security relevant and business action. Other modules write to it through
 * {@link com.mrms.audit.AuditTrail}; only administrators can read it.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Audit trail")
package com.mrms.audit;
