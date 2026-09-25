# ADR 0012: Time limits with consequences, and an outbox for e-mail and SMS

**Status:** Accepted
**Author:** Danish Husain

## Context

Release 0.1 measured how long each stage took and showed breaches on dashboards. Nothing happened when a limit passed: a claim could still sit with one official for weeks. The owner asked for deadlines "with teeth". Officials, employees and supervisors also need to be told outside the portal, reliably and without leaking health data.

## Decision

1. An **escalation module** watches every queue through a small interface (`QueueSource`) that the claim and e-NAC modules implement, so it depends on neither.
2. Three steps: reminder at 80% of the limit, breach at the limit (office, supervisor and employee told; a held record goes back to the queue if a colleague can take it), escalation to a new **Zonal Oversight** role at twice the limit.
3. Every step is a row in `sla_event` with a unique key per record, stage and step. This makes the job idempotent and safe on several servers, and gives each office a permanent record of delays.
4. E-mail and SMS go through an **outbox** written in the business transaction and sent by a background job with claiming, retries and backoff. Messages carry the title only.

## Consequences

- Delays have visible, recorded consequences and reach someone with authority, without anyone having to chase files.
- An official cannot hold a record past its limit when a colleague could do it.
- The watch adds one query per queue every 15 minutes; negligible at the expected volume.
- Real delivery needs an SMTP relay and a DLT registered SMS template, configured per deployment.
