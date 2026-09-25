# 16. Deadlines and Notifications

**Author:** Danish Husain

## 16.1 Time limits

| Stage | Worked by | Limit | Told first when it passes |
|-------|-----------|-------|---------------------------|
| With pharmacist | Pharmacists of the dispensary | 2 days | Medical Officer |
| With Medical Officer | Medical Officers of the dispensary | 2 days | (zone at twice the limit) |
| With Head of School | Head of School | 3 days | (zone at twice the limit) |
| PAO scrutiny | PAO auditors | 7 days | PAO officer |
| Awaiting sanction | PAO officers | 3 days | (zone at twice the limit) |

Limits are set in `mrms.sla.*`. A stage starts when a record enters it; a returned and resubmitted claim starts a new stage (and keeps its queue seniority).

## 16.2 What happens, and when

A background watch runs every 15 minutes (`mrms.sla.check-interval`).

| Step | When | What happens |
|------|------|--------------|
| Reminder | 80% of the limit (`reminder-percent`) | The official holding the record, or everyone who works that queue, is reminded. |
| Breach | The limit passes | The queue's officials and their supervisor in the office are told. The employee is told that the office has been reminded. A record held by one official is **put back in the queue** if a colleague can take it. The breach is recorded. |
| Escalation | Twice the limit (`escalation-factor`) | The Zonal Oversight officers of the school's education zone and the administrators are told. |

Each step happens once per record and stage. The unique key on `sla_event` makes the watch safe on several servers: whichever server records a step first sends its messages.

## 16.3 Zonal Oversight role

A new role, **Zonal Oversight Officer** (for example the Deputy Director of Education of a zone), is tied to an education zone. Administrators create these accounts and pick the zone from those used by schools.

The **Time limits** screen shows, for the officer's zone (all zones for an administrator):

- records past their limit now, longest delay first, with the office, who holds it, how late it is and whether it is escalated;
- a **Remind** button (at most once an hour per record, recorded in the audit trail and on the screen);
- the **record of delays**: breaches in the last 90 days per office and stage, and how many are still open.

Oversight officers see status, dates and names of offices and officials only; never documents, diagnoses or amounts.

## 16.4 E-mail and SMS

Important events also go out by e-mail and SMS to users who have an address or mobile number on record:

- claim returned, sanctioned, paid or rejected; e-NAC issued or returned;
- every time limit step and oversight reminder.

**Content.** E-mail and SMS carry only the title, for example "Claim MR/9900001/2026-27/000001 returned for correction", and ask the user to sign in. Remarks, diagnoses and amounts are never sent outside the portal.

**Delivery.** Messages are written to the `outbound_message` outbox in the same transaction as the event, and a background job sends them every 30 seconds. A message is claimed by one server before sending, retried after 1, 5, 15, 60 and 240 minutes, then marked failed. Logs show destinations masked (`a***@example.org`, `******3210`).

**Configuration** (each channel is `off`, `log` or live):

| Variable | Meaning |
|----------|---------|
| `MRMS_PUBLIC_BASE_URL` | Portal address used in e-mail links |
| `MRMS_MAIL_MODE` | `off`, `log` or `smtp` |
| `MRMS_MAIL_FROM` | Sender address |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP server (for example the NIC mail relay) |
| `MRMS_SMS_MODE` | `off`, `log` or `http` |
| `MRMS_SMS_URL_TEMPLATE` | Gateway address with `{mobile}`, `{message}` and `{templateId}` placeholders |
| `MRMS_SMS_TEMPLATE_ID` | DLT registered template id (commercial SMS in India must use a registered template) |

For the NIC SMS gateway, register the sender id and the message templates on the DLT platform, then set the URL template to the gateway address with its credentials and the placeholders above. The development profile logs messages instead of sending them.

## 16.5 Tests

`EscalationIntegrationTests` drives a real e-NAC through reminder, breach (with release back to the queue) and escalation, checks that nothing is sent twice, and exercises the oversight screen and reminder throttling. `OutboxTests` covers delivery, retries and masking.
