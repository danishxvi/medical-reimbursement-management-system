# ADR 0002: Server side sessions with CSRF tokens instead of JWT

**Status:** Accepted
**Author:** Danish Husain

## Context

The users are government officials acting on money and health data. We need immediate revocation (disable an account, end a stolen session, one session per account), short idle timeouts and no credentials reachable by JavaScript.

## Decision

Use Spring Security server side sessions. The session id travels in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie; the id is rotated on login; concurrent sessions are limited to one. State changing requests carry a CSRF token (double submit cookie). The SPA and the API are served from one origin through nginx, so no CORS is needed in production.

## Consequences

- Revocation is instant and complete; XSS cannot read the session.
- Sessions live in memory in release 0.1, so a single API instance is used; a shared session store (Spring Session JDBC) is on the roadmap before running several instances.
- Every write needs the CSRF header, handled centrally by the frontend API client.
