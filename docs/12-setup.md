# 12. Setup and Deployment

**Author:** Danish Husain

## 12.1 Local development (no Docker needed)

Requirements: Java 21, Maven 3.9+, Node.js 22+ (24 recommended).

```bash
# Terminal 1: API on http://localhost:8080 with an H2 file database and demo data
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
# Terminal 2: web app on http://localhost:5173 (proxies /api to the API)
cd frontend
npm install
npm run dev
```

The `dev` profile seeds fictional offices and one account for every role. All demo accounts use the password **`Demo@Pass2026`** (public, development only). The login page shows one click buttons for them in development builds.

| Login ID | Role | Office |
|----------|------|--------|
| `EMP1001` | Employee (with two dependents) | Demo Sarvodaya Vidyalaya |
| `EMP1002` | Employee | Demo Sarvodaya Vidyalaya |
| `EMP2001` | Employee | Demo Government Girls SSS |
| `HOS9900001` | Head of School | Demo Sarvodaya Vidyalaya |
| `HOS9900002` | Head of School | Demo Government Girls SSS |
| `PHARM01` | Pharmacist | Demo DGEHS Dispensary |
| `MO01` | Medical Officer | Demo DGEHS Dispensary |
| `AUD01`, `AUD02` | PAO Auditor | PAO No. 13 (Demo) |
| `PAO01` | PAO Officer | PAO No. 13 (Demo) |
| `ADMIN` | Administrator | Directorate |

To start again from an empty database, stop the API and delete `backend/data`.

### A complete walk through

1. `EMP1001`: **Request e-NAC**, upload any PDF as the prescription, list a medicine.
2. `PHARM01`: **Prescription queue**, take next, mark the item *Not available*, send.
3. `MO01`: take next, **Countersign and issue** (password).
4. `EMP1001`: **New claim**, add the medicine from the e-NAC, upload a bill, attach a DGEHS card copy, accept the undertaking, submit.
5. `HOS9900001`: **Verification queue**, take next, fill the calculation sheet, certify (password).
6. `AUD01`: take next, admit amounts, send to officer.
7. `PAO01`: take next, **Sanction**; then **Budgets and payments**, open the school, record an allocation and **Pay**.
8. `EMP1001`: the claim shows *Paid* with the full history.

## 12.2 Tests

```bash
cd backend && mvn verify      # unit, integration, security and module boundary tests
cd frontend && npm run lint && npm run build
```

`ModularityTests` also writes component diagrams of the modules to `backend/target/spring-modulith-docs`.

## 12.3 Production like stack with Docker

```bash
cp .env.example .env
# edit .env: database password, MRMS_STORAGE_KEY (openssl rand -base64 32),
# first administrator login and a temporary password (12+ characters)
docker compose up -d --build
```

Open `http://localhost:8080` (or the port set in `MRMS_WEB_PORT`) and sign in as the administrator; you will be asked to change the password. Then create PAOs, schools, dispensaries, officials and employees from the administration screens.

What the stack does:

- **db**: PostgreSQL 17 on an internal network only.
- **backend**: the API as a non root user on a read only file system; uploaded files are stored encrypted in the `storage` volume.
- **web**: nginx serving the app with a strict Content Security Policy and proxying `/api`; published on the loopback address only.

## 12.4 Going live checklist

- [ ] TLS 1.2+ gateway in front of `web` with HSTS; WAF with OWASP core rules
- [ ] Database role with least privilege; `audit_entry` insert only (the migration already blocks update and delete)
- [ ] `MRMS_STORAGE_KEY` in a secrets manager; tested restore of database and storage together
- [ ] Antivirus scanning of uploads at the gateway
- [ ] Central logging and alerts on lockouts, failed signatures and audit chain verification
- [ ] Security audit by a CERT-In empanelled auditor
- [ ] Privacy notice, retention policy and DPIA under the DPDP Act, 2023

## 12.5 Configuration reference

| Variable | Default | Purpose |
|----------|---------|---------|
| `MRMS_DB_URL`, `MRMS_DB_USERNAME`, `MRMS_DB_PASSWORD` | none | PostgreSQL connection |
| `MRMS_STORAGE_KEY` | none (required) | Base64 32 byte AES key for uploaded files |
| `MRMS_STORAGE_ROOT` | `./storage` | Where encrypted files are written |
| `MRMS_ADMIN_USERNAME`, `MRMS_ADMIN_PASSWORD` | empty | First administrator (only if none exists) |
| `MRMS_ALLOWED_ORIGINS` | empty | CORS origins; leave empty when app and API share a domain |
| `MRMS_FORWARD_HEADERS` | `none` | `native` behind a trusted proxy |
| `MRMS_PORT` | `8080` | API port |

Business settings (SLA days per stage, 90 day submission window, lockout thresholds, rate limits) are under `mrms.*` in `backend/src/main/resources/application.yml`.
