# 6. Security Design

**Author:** Danish Husain

MRMS handles health information and government money. Security is designed in at every layer and verified by automated tests (`SecurityIntegrationTests`, `AuditChainTests`, `FileInspectorTests`, `SafeTextTests`, `PasswordPolicyTests`, `ModularityTests`).

## 6.1 Threat model (summary)

| Threat | Example | Main controls |
|--------|---------|---------------|
| Account takeover | Password guessing, credential stuffing | Argon2id hashing, lockout after 5 failures, per IP login rate limit, generic error messages, strong password policy with history |
| Session attacks | Session fixation, hijacking, CSRF | Server side sessions in HttpOnly Secure SameSite=Strict cookies, session id rotation on login, single active session, CSRF tokens, 15 minute idle timeout |
| Broken access control | An HoS opening another school's claim, an employee reading someone's bill | Role rules at URL and method level, object level scope checks in every service, out of scope records reported as not found |
| Insider manipulation | Favouring a claim, editing a decision, disowning a decision | Strict FIFO queues, attributed decisions, password confirmation for signatures, maker checker at PAO, hash chained append only audit trail |
| Malicious uploads | Script inside a PDF, disguised executable | Type detected from content, extension must match, active PDF content rejected, size limit, served as attachment with nosniff and a sandbox CSP |
| Data theft at rest | Stolen disk or backup | AES-256-GCM encryption of every stored file, only masked bank account stored |
| Injection | SQL injection, XSS, disguised text | JPA parameter binding only, validation and a global text filter on every input, React output escaping, strict Content Security Policy without inline scripts |
| Information leakage | Stack traces, user enumeration | Problem responses with reference ids, identical login failure messages and timing |
| Duplicate claims | Same bill claimed twice | File fingerprint, bill number, vendor and date checks across active claims |
| Supply chain | Vulnerable dependency | Dependabot, `npm audit`, CodeQL, pinned lock file |

## 6.2 Authentication

- **Passwords** are hashed with **Argon2id** (19 MiB memory, 2 iterations) behind a delegating encoder, so the algorithm can be upgraded later; hashes are upgraded transparently on the next successful login.
- **Policy:** at least 12 characters with upper case, lower case, digit and symbol; must not contain the login ID; must not be a common password; must differ from the last 5 passwords.
- **Temporary passwords** are generated randomly when an account is created or reset, shown once to the administrator, and must be changed at first sign in (the API blocks everything else until then).
- **Lockout:** 5 failed attempts lock the account for 15 minutes. Locked, disabled and unknown accounts produce exactly the same response, and a dummy hash is checked for unknown users so response time does not reveal valid IDs.
- **Step up confirmation:** certifying (HoS), countersigning (MO), sanctioning, rejecting and releasing payments (PAO officer) require the password again. A wrong password counts towards lockout.

## 6.3 Sessions and CSRF

- Session cookie `MRMS_SESSION`: `HttpOnly`, `Secure`, `SameSite=Strict`, path `/`, 15 minute idle timeout. No tokens are kept in browser storage.
- On login the session id is rotated, the CSRF token is rotated, and any other session of the same account is ended.
- Changing a password or being disabled ends all other sessions of the account.
- CSRF protection uses the double submit pattern: the `XSRF-TOKEN` cookie must be echoed in the `X-XSRF-TOKEN` header on every state changing request, including login.

## 6.4 Authorisation

Three layers, each sufficient on its own for the rule it enforces:

1. **URL rules** in `SecurityConfig` (for example `/api/admin/**` needs `ADMIN`, claim creation needs `EMPLOYEE`).
2. **Method rules** with `@PreAuthorize` on every controller method.
3. **Object rules** in services:
   - employees see only their own claims, certificates and documents;
   - a HoS sees non draft claims of their own school;
   - PAO officials see claims of schools served by their PAO once certified by the school;
   - dispensary staff see requests made to their dispensary;
   - administrators see aggregates only, never individual medical claims.

Reviewers can act only on the record assigned to them through the queue.

## 6.5 Input validation and sanitisation

1. **Every string read from JSON passes a global filter** (`SafeText`, registered as a JSON module so no endpoint can forget it). Text is normalised to Unicode NFC, and a value containing control characters (other than tab and line breaks), bidirectional override or isolate characters, unpaired surrogates or noncharacters is refused with `400 UNSAFE_TEXT`. Refusing instead of silently removing keeps stored text identical to what the user saw.
2. **Every request body is validated** with Bean Validation (lengths, formats such as IFSC, MICR, account and phone numbers, ranges for amounts and dates) before it reaches a service; all field problems are returned together.
3. **Business rules are validated in the aggregate**, so an invalid state cannot be reached through any endpoint.
4. **Uploaded file names** are reduced to a safe display name: directories, control, direction and reserved characters removed, length bounded. The stored file never uses the supplied name.
5. **Output encoding happens at the edge:** React escapes everything it renders, the API only returns JSON, and SQL is always parameter bound. Text is not HTML escaped in the database, which would corrupt it for the printable forms.

## 6.6 Documents

1. Size limit 5 MB (enforced by the servlet container and again by the service).
2. Type is detected from the first bytes (PDF, JPEG, PNG); anything else is rejected, and the file name extension must agree.
3. PDFs containing `/JavaScript`, `/JS`, `/Launch`, `/EmbeddedFile(s)`, `/RichMedia`, `/XFA`, `/SubmitForm`, `/ImportData` or `/GoToE` are rejected, including names hidden with `#xx` escapes. Content inside compressed object streams is not visible to this check; the virus scan below covers it.
4. **Every upload is scanned by ClamAV** before anything is written. The file is streamed to the ClamAV daemon in memory (INSTREAM); an infected file is refused with `MALWARE_DETECTED`, never stored, and the attempt is audited. If the scanner is unreachable, uploads are refused (`SCANNER_UNAVAILABLE`, fail closed) rather than accepted unscanned. Scanning can be switched off only in the development profile, where files are marked `NOT_SCANNED`. The scan result and engine are stored with each document.
5. **Standard names.** Each upload gets a standard name, by default `EMPLOYEEID_CATEGORY_YYYYMMDD_NN` (for example `EMP1001_BILL_20260925_01.pdf`); inside a claim, documents are named after the claim (`MR-9900001-2026-27-000001_BILL_01.pdf`). The patterns are configurable (`MRMS_DOC_UPLOAD_PATTERN`, `MRMS_DOC_CLAIM_PATTERN`) but can only contain letters, digits, separators and known tokens, so a name can never form a path. The uploader's own file name is kept only for reference.
6. Files are stored under random server generated names, sharded into directories, and encrypted with **AES-256-GCM**. The storage name is bound as associated data, so a file moved or renamed on disk fails to decrypt.
7. Downloads are always `Content-Disposition: attachment` with `X-Content-Type-Options: nosniff` and `Content-Security-Policy: default-src 'none'; sandbox`.
8. Every view of a document is recorded in the audit trail.

## 6.7 Audit trail

- Every login, logout, failed login, account change, upload, document view, e-NAC decision and claim transition is written to `audit_entry`.
- Each entry stores `hash = SHA-256(previous hash | canonical fields)`. Appending locks a single head row, so concurrent writers (also on several servers) cannot fork the chain.
- On PostgreSQL a trigger blocks `UPDATE`, `DELETE` and `TRUNCATE` on the table.
- Administrators can run **Verify chain**; any edited or deleted row is reported with its id.
- Entries are written in the same transaction as the action, so the log never records something that did not happen and never misses something that did.

## 6.8 Transport and browser

API responses carry: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `Strict-Transport-Security` (on HTTPS), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, a restrictive `Permissions-Policy`, and cross origin opener and resource policies.

The web server (nginx) sends a strict CSP for the application: scripts, styles, fonts and connections from the same origin only, no inline scripts, no frames, no plugins. Fonts are bundled, so the app makes no request to any third party.

## 6.9 Abuse protection

- Per IP fixed window rate limits: 10 login attempts and 300 API requests per minute (configurable). Behind the gateway the real client address comes from the trusted proxy headers only.
- Queues cannot be bypassed: there is no endpoint that assigns an arbitrary record to a reviewer.

## 6.10 Secrets and configuration

- The base configuration contains no secrets; database credentials, the storage key and the first administrator come from environment variables. Startup fails if the storage key is missing or not 32 bytes.
- The `dev` profile is the only one with fixed values (public demo password and a public development storage key). It must never run on a server.
- `.env`, keys, local databases and uploads are ignored by git; CI fails if such files are committed.

## 6.11 Privacy

- Only the last four digits of the salary account are stored.
- Health data is shown only to the people who need it for a claim (see 6.4).
- Retention and deletion policy, consent notices and a data protection impact assessment are required before go live under the DPDP Act, 2023 (roadmap item).

## 6.12 Operational recommendations

- Terminate TLS 1.2+ at the gateway with HSTS preload; enable a WAF with OWASP core rules.
- Give the application database role only `SELECT, INSERT, UPDATE` on business tables and `SELECT, INSERT` on `audit_entry`.
- Back up the database and the encrypted storage together; keep the storage key in a secrets manager or HSM, separate from backups.
- Ship application logs and audit events to a SIEM; alert on `LOGIN_BLOCKED`, `STEP_UP_FAILED` and chain verification failures.
- Conduct a CERT-In empanelled security audit before production use, as required for government applications.

## 6.13 Reporting

See [SECURITY.md](../SECURITY.md).
