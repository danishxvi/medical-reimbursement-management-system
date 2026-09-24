# ADR 0001: Modular monolith instead of microservices

**Status:** Accepted
**Author:** Danish Husain

## Context

The system has clear business areas (identity, organisation, documents, e-NAC, claims, budget, notifications, audit) but a small team and a government hosting environment where every extra service means another security review, another deployment and another thing to patch. Workflows cross several areas inside one user action (certifying a claim also writes the timeline, the audit trail and notifications) and must be atomic.

## Decision

Build one Spring Boot application split into modules with enforced boundaries (Spring Modulith). Each module owns its tables, exposes a small API in its base package and keeps everything else in `internal`. `ModularityTests` fails the build on a boundary violation or a dependency cycle. Cross module reactions use in process domain events.

## Consequences

- One deployable, one database transaction per user action, simple operations.
- Boundaries are real, so a module (for example budget or notification) can later be extracted into a service if load or ownership demands it.
- All modules scale together; acceptable at the expected load.
