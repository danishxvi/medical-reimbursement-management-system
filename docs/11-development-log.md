# 11. Development Log

**Project lead and developer:** Danish Husain

## How the project was built

The problem comes from the real experience of a teacher under the Directorate of Education, Delhi. The paper forms in use today (DGEHS Annexure I and II, the school's medical application form, the calculation sheet, the employee undertaking and the certificate by HoS/HoO) were used as the source for every field, rule and legal text in the system.

Work followed this order:

1. Problem analysis and the target process (documents 01 and 02).
2. Database schema and backend modules, each with its own tests.
3. End to end integration test of the full journey, from e-NAC to payment, which caught real defects (attachment updates tripping a unique constraint, timestamp precision drift) before any UI existed.
4. The React interface on a small in house design system.
5. A manual walk through of every role in the browser, which caught and fixed session handling and small screen layout issues.
6. Deployment files, CI and documentation.

## Tools

- Java 21, Spring Boot, Maven; Node.js, React, Vite
- GitHub Actions for continuous integration
- AI pair programming assistance was used during development for code suggestions, reviews and documentation drafts.

## Milestones

| Date | Milestone |
|------|-----------|
| 2026-09-24 | Problem statement and proposed solution |
| 2026-09-24 | Backend foundation: security, audit, identity, organisation, documents |
| 2026-09-24 | e-NAC, claims workflow, budget, notifications, dashboards |
| 2026-09-24 | Integration and security test suites |
| 2026-09-25 | React frontend for all roles |
| 2026-09-25 | Browser walk through fixes, deployment files, CI, documentation |
