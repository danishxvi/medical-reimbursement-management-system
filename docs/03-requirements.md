# 3. Software Requirements Specification

**Author:** Danish Husain
**Version:** 0.1 (first release scope)

Requirement ids are stable and are referenced from tests, commits and the roadmap. "Must" is mandatory for the first release; "Should" is planned for the first release where time allows.

---

## 3.1 Actors

| Actor | Description |
|-------|-------------|
| Employee | Teaching or non teaching staff member of a DoE school holding a DGEHS card. |
| Head of School (HoS) | Head of the school; also the Drawing and Disbursing Officer (DDO) for the calculation sheet. |
| Pharmacist | Staff of a DGEHS dispensary (AMA) who checks stock for prescribed items. |
| Medical Officer (MO) | Medical Officer In Charge of the dispensary who countersigns the certificate. |
| PAO Auditor | Dealing official at the Pay and Accounts Office who scrutinises claims (maker). |
| PAO Officer | Sanctioning authority at the PAO; manages budgets and payments (checker). |
| Administrator | Directorate official who manages accounts and master data. Sees aggregates only. |

## 3.2 Functional requirements

### Accounts and access
| Id | Requirement | Priority |
|----|-------------|----------|
| FR-01 | Users sign in with their Employee ID (employees) or issued login ID (officials) and a password. | Must |
| FR-02 | Accounts created by an administrator start with a temporary password that must be changed at first sign in. | Must |
| FR-03 | Each account has one role and is tied to exactly one office (school, dispensary or PAO) according to that role. | Must |
| FR-04 | Administrators can reset passwords (issuing a one time temporary password), unlock and disable accounts. | Must |
| FR-05 | A new sign in ends any older session of the same account. | Must |

### Master data
| Id | Requirement | Priority |
|----|-------------|----------|
| FR-10 | Administrators maintain PAOs, schools (each linked to one PAO) and dispensaries. | Must |
| FR-11 | Administrators onboard employees with service, DGEHS card and bank details (only the last four digits of the account are stored). | Must |
| FR-12 | Employees can edit their contact details and add or remove family members (dependents). | Must |

### e-NAC (dispensary)
| Id | Requirement | Priority |
|----|-------------|----------|
| FR-20 | An employee submits a prescription (scan) with its items to a dispensary for a non availability certificate. | Must |
| FR-21 | The pharmacist marks every item as available, not available or not admissible; not admissible needs a reason. | Must |
| FR-22 | The MO countersigns (with password confirmation) or sends the certificate back to the same pharmacist. | Must |
| FR-23 | The pharmacist can return an unusable prescription to the employee, who corrects and resubmits it. | Must |
| FR-24 | Every item decision records who made it and when. | Must |

### Claims
| Id | Requirement | Priority |
|----|-------------|----------|
| FR-30 | The claim form is pre filled from the employee profile; the employee enters patient, treatment and bill details. | Must |
| FR-31 | Claims cover OPD and indoor treatment, with the four charge categories of Annexure II. | Must |
| FR-32 | Each bill carries an uploaded document; medicines for OPD treatment are linked to a not available e-NAC item, or to a scanned legacy NAC. | Must |
| FR-33 | Drafts can be saved and resumed. | Must |
| FR-34 | Submission requires acceptance of the employee undertaking. | Must |
| FR-35 | The system validates the claim before submission (dates, 90 day window, card validity, required documents, e-NAC coverage, duplicates) and lists every problem at once. | Must |
| FR-36 | The Annexure I check list is derived automatically from the attached documents. | Must |
| FR-37 | A returned claim is corrected and resubmitted; it keeps its original submission time (queue seniority). | Must |
| FR-38 | An employee can withdraw a claim that is returned or not yet taken by the HoS. | Must |
| FR-39 | The same bill (file or bill number, vendor and date) or e-NAC item cannot be in two active claims. | Must |

### Review
| Id | Requirement | Priority |
|----|-------------|----------|
| FR-40 | HoS, PAO auditors and PAO officers work through first come first served queues; "take next" always assigns the oldest waiting record. | Must |
| FR-41 | The HoS fills the calculation sheet (DGEHS rate and restricted amount per item) and certifies with password confirmation. | Must |
| FR-42 | Reviewers return claims for correction with standard reason codes and remarks. | Must |
| FR-43 | The PAO auditor admits an amount per item (reason required when less than the school figure) and recommends sanction or rejection. | Must |
| FR-44 | A different PAO officer sanctions or rejects with password confirmation, or sends the claim back to the auditor. | Must |
| FR-45 | Every transition is shown on the claim timeline with actor, time, reasons and remarks. | Must |

### Budget and payment
| Id | Requirement | Priority |
|----|-------------|----------|
| FR-50 | The school's budget demand is calculated from claims in the pipeline minus the available balance. | Must |
| FR-51 | The HoS forwards the demand to the PAO; the PAO officer acknowledges it. | Must |
| FR-52 | The PAO officer records allocations against sanction orders. | Must |
| FR-53 | A payment run pays sanctioned claims oldest first and stops at the first claim the balance cannot cover. | Must |

### Oversight
| Id | Requirement | Priority |
|----|-------------|----------|
| FR-60 | Each role has a dashboard with counts, amounts, SLA breaches and average days to payment. | Must |
| FR-61 | Users receive in app notifications on every status change that concerns them. | Must |
| FR-62 | Administrators search the audit trail and verify its hash chain. | Must |
| FR-63 | SMS and e-mail notifications. | Should (roadmap) |

## 3.3 Non functional requirements

| Id | Category | Requirement |
|----|----------|-------------|
| NFR-01 | Security | Targets OWASP ASVS level 2 for authentication, session management, access control, input validation and file upload. Details in [06-security.md](06-security.md). |
| NFR-02 | Privacy | Medical data is visible only to the employee and the officials handling the claim; administrators see aggregates. Aligned with the Digital Personal Data Protection Act, 2023. |
| NFR-03 | Integrity | Every action is written to a tamper evident, append only audit trail. |
| NFR-04 | Availability | First release runs one API instance with sessions in memory; a shared session store for several instances is on the roadmap. Daily database backups with point in time recovery (deployment guide). |
| NFR-05 | Performance | Target: common pages respond within 1 second at 200 concurrent users on a single instance. |
| NFR-06 | Usability | Works on phones and desktops; plain language; keyboard accessible; honours reduced motion; WCAG 2.1 AA contrast (saffron and white palette). |
| NFR-07 | Maintainability | Modular monolith with verified module boundaries; automated tests in CI. |
| NFR-08 | Portability | Runs on PostgreSQL in production and on H2 for local development without Docker. |
| NFR-09 | Auditability | Rules (SLA days, submission window) are configuration, not code. |

## 3.4 Constraints and assumptions

- Payment is recorded in MRMS with a batch reference; the actual credit happens through the existing salary process until treasury integration is built.
- One role per account. An HoS who wants to claim for themselves uses a separate employee account (see roadmap for multi role accounts).
- DGEHS rate lists are applied manually by the HoS in the first release (rate master on the roadmap).
