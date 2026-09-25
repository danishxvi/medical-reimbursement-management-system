# ADR 0010: Store sessions in the database

**Status:** Accepted (extends [ADR 0002](0002-server-side-sessions.md))
**Author:** Danish Husain

## Context

ADR 0002 chose server side sessions over tokens in the browser. In release 0.1 those sessions lived in the memory of the single API process. A state wide rollout needs more than one API instance behind a load balancer, and restarts for updates should not sign everyone out. The rules that depend on sessions, one session per account and forced sign out after a password change or a disable, must hold across every instance.

## Decision

Sessions are stored in PostgreSQL with Spring Session JDBC (tables `spring_session` and `spring_session_attributes`, created by Flyway migration V5). Sessions are indexed by the login ID, and Spring Security's concurrency control uses a registry backed by that index, so a new login or a forced sign out ends the older session whichever instance holds the request.

The session cookie is configured explicitly (`MRMS_SESSION`, HttpOnly, Secure, SameSite=Strict, path `/`) and so is the 15 minute idle timeout, because Spring Session does not take these from the servlet container settings. Integration tests assert both.

## Consequences

- Any number of API instances can run; no sticky sessions are needed.
- Every request touches the session row (last access time); at the expected load this is negligible next to the business queries. Expired rows are removed by a scheduled cleanup every minute.
- The application database role needs `DELETE` on the two session tables (and on nothing else outside the business tables).
- A Redis store could replace JDBC later without code changes if session traffic ever becomes significant.
