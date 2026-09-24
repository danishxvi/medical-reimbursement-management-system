# ADR 0008: One portable schema for PostgreSQL and H2

**Status:** Accepted
**Author:** Danish Husain

## Context

Contributors should be able to run the whole system with only Java and Node installed, while production uses PostgreSQL.

## Decision

Write the Flyway baseline in portable SQL (identity columns, `uuid`, `numeric`, `timestamp with time zone`, sequences) and run H2 in PostgreSQL mode for development and tests. Database specific features live in `db/vendor/{vendor}` (for example the PostgreSQL trigger that makes the audit table append only), with a matching no op script for H2 so both keep the same version history. Hibernate validates the schema at start up.

## Consequences

- `mvn spring-boot:run` works on a fresh machine; CI needs no database service.
- The PostgreSQL stack is verified with Docker Compose; vendor specific scripts must be written twice (one of them a no op).
