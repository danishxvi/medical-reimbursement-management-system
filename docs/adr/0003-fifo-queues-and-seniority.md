# ADR 0003: Strict FIFO queues and seniority that survives a return

**Status:** Accepted
**Author:** Danish Husain

## Context

Two of the worst problems are partiality (favoured employees are served first) and returned bills losing their turn and waiting for the next budget.

## Decision

- Every review stage has a queue ordered by the record's first submission time. That time is written once and never changed.
- Reviewers cannot pick a record. "Take next" returns the record they already hold or assigns the oldest untaken one. Actions are allowed only on the assigned record.
- A returned claim is corrected and resubmitted; it keeps its original time and therefore goes to the head of every queue it passes through again.
- SLA timers per stage are measured from when the record entered the stage and shown on dashboards.

## Consequences

- Favouritism is structurally impossible and delays become visible and attributable.
- A reviewer who cannot decide a claim must return it with reasons or release it; they cannot quietly park it and take newer ones.
- Several reviewers in one office share the queue; optimistic locking resolves the rare race when two take the same head at once.
