# Medical Reimbursement Management System (MRMS)

A secure, transparent and accountable web portal that digitises the outpatient medical reimbursement process for employees of the **Directorate of Education, Government of NCT of Delhi**, from dispensary verification to payment.

> Status: under active development. See the [roadmap](docs/10-roadmap.md).

**Author:** Danish Husain

---

## Why

Today a medical reimbursement claim is a paper file that moves by hand from the dispensary to the school to the Pay and Accounts Office (PAO). That creates delays, partiality, middlemen and bribery, and a single mistake sends the claim back to wait for the next budget. The full analysis is in the [problem statement](docs/01-problem-statement.md).

## What MRMS does

| For | Capability |
|-----|------------|
| Employee | Login with Employee ID, pre filled claim form, upload prescriptions and bills (PDF / image), live tracking, amend and resubmit returned claims without losing queue position. |
| Dispensary | **e-NAC**: pharmacist marks each prescribed item, Medical Officer countersigns. Every decision is attributed. |
| Head of School | Strict first come first served verification queue, one click certification, automatic budget demand. |
| PAO | Maker checker scrutiny (auditor then officer), item level admissibility, budget allocation and oldest first payment runs. |
| Directorate | Dashboards, SLA breach monitoring, tamper evident audit trail. |

Details: [proposed solution](docs/02-proposed-solution.md).

## Architecture at a glance

- **Backend:** Java 21, Spring Boot 4, modular monolith (Spring Modulith verified module boundaries), Spring Security, Spring Data JPA, Flyway.
- **Frontend:** React, TypeScript, Vite, TanStack Query, React Router, Framer Motion.
- **Database:** PostgreSQL in production, H2 (PostgreSQL mode) for zero setup local development.

See [architecture](docs/04-architecture.md).

## Documentation

| # | Document |
|---|----------|
| 1 | [Problem statement](docs/01-problem-statement.md) |
| 2 | [Proposed solution](docs/02-proposed-solution.md) |
| 3 | [Requirements specification](docs/03-requirements.md) |
| 4 | [Architecture](docs/04-architecture.md) |
| 5 | [Claim and e-NAC workflow](docs/05-workflow.md) |
| 6 | [Security design](docs/06-security.md) |
| 7 | [Data model](docs/07-data-model.md) |
| 8 | [API reference](docs/08-api.md) |
| 9 | [UI design system](docs/09-ui-design-system.md) |
| 10 | [Roadmap](docs/10-roadmap.md) |
| 11 | [Development log](docs/11-development-log.md) |
| ADR | [Architecture decision records](docs/adr/) |

## Getting started

Instructions are in [docs/12-setup.md](docs/12-setup.md).

## Security

Please report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
