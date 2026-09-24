# Architecture Decision Records

Each record explains one significant decision: the context, the choice, and what it costs. Records are never rewritten; a changed decision gets a new record that supersedes the old one.

| # | Decision | Status |
|---|----------|--------|
| [0001](0001-modular-monolith.md) | Modular monolith instead of microservices | Accepted |
| [0002](0002-server-side-sessions.md) | Server side sessions with CSRF tokens instead of JWT | Accepted |
| [0003](0003-fifo-queues-and-seniority.md) | Strict FIFO queues and seniority that survives a return | Accepted |
| [0004](0004-electronic-nac.md) | Item level electronic NAC with named decisions | Accepted |
| [0005](0005-decouple-processing-from-budget.md) | Decouple claim processing from budget arrival | Accepted |
| [0006](0006-hash-chained-audit-trail.md) | Hash chained audit trail written in the business transaction | Accepted |
| [0007](0007-encrypted-document-storage.md) | Encrypted file storage with content based validation | Accepted |
| [0008](0008-portable-schema.md) | One portable schema for PostgreSQL and H2 | Accepted |

Author: Danish Husain
