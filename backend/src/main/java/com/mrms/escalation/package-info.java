/**
 * Escalation: time limits with consequences.
 *
 * <p>A background watch looks at every queue stage. Close to the limit the
 * officials are reminded; when it passes the record is flagged, the officials
 * and their supervisor are told, the employee is informed, and a record held
 * by one official is put back in the queue if a colleague can take it. At
 * twice the limit it goes to the Zonal Oversight officer. Every step is
 * recorded, which gives each office a performance record.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Escalation and time limits")
package com.mrms.escalation;
