# ADR 0006: Hash chained audit trail written in the business transaction

**Status:** Accepted
**Author:** Danish Husain

## Context

The audit record must be trustworthy even against someone with database access, and it must never disagree with what actually happened.

## Decision

- Every entry stores `SHA-256(previous hash | canonical fields)`. A single head row is locked while appending, so writers are serialised even across several application instances and the chain cannot fork.
- Entries are written in the same transaction as the business change: both commit or neither does.
- On PostgreSQL a trigger rejects UPDATE, DELETE and TRUNCATE on the table.
- Administrators can verify the whole chain from the UI.

## Consequences

- Tampering is detectable and pinpointed to an entry.
- Audit writes are serialised; at the expected volume this is well within limits, and the lock is held only for the insert.
- The canonical format is frozen; changing it requires a new chain segment.
