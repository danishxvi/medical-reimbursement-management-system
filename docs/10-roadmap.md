# 10. Roadmap

**Author:** Danish Husain

## Release 0.1 (this repository)

- [x] Problem statement, requirements, architecture, workflow, security and design documents
- [x] Modular monolith backend with verified module boundaries
- [x] Accounts, lockout, password policy, single session, CSRF, security headers, rate limiting
- [x] Master data: PAOs, schools, dispensaries, employees, dependents
- [x] Encrypted, content validated document storage
- [x] e-NAC with item level decisions and MO countersignature
- [x] Claim form following the DGEHS forms, draft, validation, duplicate detection, undertaking
- [x] FIFO queues for HoS, PAO auditor, PAO officer, pharmacist, MO; SLA tracking
- [x] Calculation sheet and HoS certificate; maker checker at the PAO; password confirmed signatures
- [x] Return for correction with reason codes; resubmission keeps seniority
- [x] Budget demand from real claims, allocations, oldest first payment runs
- [x] Hash chained, append only audit trail with verification
- [x] Global input sanitisation of every JSON string (control and direction characters refused, NFC normalisation)
- [x] Role dashboards, in app notifications
- [x] Saffron and white, animated, responsive React UI with rounded boxes
- [x] CI (tests, lint, build, dependency audit, CodeQL), Dependabot, Docker Compose deployment

## Release 0.2: fit for a pilot

- [ ] Aadhaar based e-Sign (or DSC) for the HoS certificate, MO countersignature and sanction, replacing password confirmation
- [ ] SMS and e-mail notifications (NIC SMS gateway), including SLA breach alerts to the next level
- [x] Printable PDF of the complete claim (Annexure I, Annexure II, calculation sheet, undertaking, HoS certificate, PAO decision, history, document index with SHA-256)
- [x] DGEHS rate master so the calculation sheet is pre filled with approved rates ([rate list](14-rate-list.md))
- [ ] Escalation: claims past SLA move to the Zonal or District Deputy Director's view
- [ ] Employee grievance and dispute of an e-NAC item decision
- [ ] Bulk import of schools and employees from existing HR data (CSV)
- [x] Antivirus scanning of uploads (ClamAV)
- [ ] PDF re rendering of uploads
- [x] Shared session store (Spring Session JDBC) to run several API instances
- [ ] Data retention rules and privacy notice under the DPDP Act, 2023

## Release 0.3: integration

- [ ] Payment through the treasury system (PFMS / e-Kosh) with bank credit confirmation
- [ ] Single sign on with the Directorate's employee portal
- [ ] Multi role accounts (an HoS who is also a claimant) with separation of duties
- [ ] Hindi interface
- [ ] OCR of bills to pre fill bill number, date and amount
- [ ] Analytics: dispensary stock outs by medicine, average processing time by office, common return reasons
- [ ] Indoor (hospitalisation) claims with package rates and medical advances

## Ideas under consideration

- Public dashboard of anonymised processing times per school and PAO, to create positive pressure
- Mobile app wrapper for photographing bills
- Automatic reminder to employees before the 90 day submission window closes
