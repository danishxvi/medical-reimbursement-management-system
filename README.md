# Medical Reimbursement Management System (MRMS)

A secure, transparent and accountable web portal that digitises the outpatient medical reimbursement process for employees of the **Directorate of Education, Government of NCT of Delhi**, from the dispensary's non availability certificate to payment.

**Author:** Danish Husain

> **Noncommercial use only.** This repository is public for study and review. Any commercial use, or any use that earns money, is prohibited. See [LICENSE](LICENSE) (PolyForm Noncommercial 1.0.0) and [DISCLAIMER.md](DISCLAIMER.md). MRMS is not an official Government of NCT of Delhi product.

> Status: release 0.1, functional end to end. Start with the **[design document](DESIGN.md)**, the single entry point to the project; see the [roadmap](docs/10-roadmap.md) for what comes next.

---

## Why

Today a medical reimbursement claim is a paper file that moves by hand from the dispensary to the school to the Pay and Accounts Office (PAO). That creates delays, partiality, middlemen and bribery, and a single mistake sends the claim back to wait for the next budget. The full analysis is in the [problem statement](docs/01-problem-statement.md).

## What MRMS does

| For | Capability |
|-----|------------|
| Employee | Sign in with Employee ID, pre filled claim form, upload prescriptions and bills (PDF or photo), live tracking with queue position, correct and resubmit a returned claim without losing its place. |
| Dispensary | **e-NAC**: the pharmacist marks each prescribed item, the Medical Officer countersigns. Every decision carries a name and a time. |
| Head of School | Strict first come first served queue, calculation sheet with DGEHS rates, certificate signed with a password, budget demand calculated from real claims. |
| PAO | Maker checker scrutiny (auditor then a different officer), item level admission, allocations against sanction orders, oldest first payment runs. |
| Directorate | Dashboards with service level breaches, account and master data administration, tamper evident audit trail with one click verification. |

Details: [proposed solution](docs/02-proposed-solution.md) and [workflow](docs/05-workflow.md).

## Highlights

- **No picking, no parking.** Reviewers can only "take next", which hands over the oldest waiting record.
- **Returned is not restarted.** A corrected claim keeps its original submission time and goes to the front of every queue.
- **Money does not block checking.** Claims are verified and sanctioned any time; only payment waits for funds.
- **Every decision is signed and recorded** in a hash chained, append only audit trail.
- **Security by default:** Argon2id, lockout, single session, CSRF protection, global input sanitisation, strict CSP and headers, content based upload validation, AES-256-GCM encryption at rest, object level access checks. See [security design](docs/06-security.md).

## Architecture at a glance

- **Backend:** Java 21, Spring Boot 4, modular monolith with Spring Modulith verified boundaries, Spring Security 7, JPA, Flyway.
- **Frontend:** React 19, TypeScript, Vite, TanStack Query, React Router, React Hook Form, Motion; saffron and white design system with rounded boxes and animation on every element.
- **Data:** PostgreSQL in production, H2 (PostgreSQL mode) for zero setup development.
- **Delivery:** Docker Compose (non root, read only containers), GitHub Actions CI, CodeQL, Dependabot.

See [architecture](docs/04-architecture.md).

## Quick start

```bash
# API with demo data on http://localhost:8080
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Web app on http://localhost:5173
cd frontend && npm install && npm run dev
```

Demo accounts and a guided walk through are in the [setup guide](docs/12-setup.md).

## Documentation

| # | Document |
|---|----------|
| 0 | [Design document (start here)](DESIGN.md) |
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
| 12 | [Setup and deployment](docs/12-setup.md) |
| 13 | [Paper form to system mapping](docs/13-form-mapping.md) |
| ADR | [Architecture decision records](docs/adr/README.md) |

## Licence

PolyForm Noncommercial License 1.0.0: free for noncommercial use, commercial use prohibited. See [LICENSE](LICENSE) and [DISCLAIMER.md](DISCLAIMER.md).

## Contributing and security

See [CONTRIBUTING.md](CONTRIBUTING.md). Please report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).

## Acknowledgements

Built by Danish Husain from the day to day experience of school staff in Delhi. AI pair programming assistance was used during development for code suggestions, reviews and documentation drafts.
