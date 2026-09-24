# 8. API Reference

**Author:** Danish Husain

Base path `/api`. JSON in and out. Errors use RFC 9457 problem responses with an extra `code` field (and `fields` for validation errors):

```json
{ "status": 422, "code": "CLAIM_INCOMPLETE", "detail": "Attach a copy of your DGEHS card. Bill B-7 is dated outside the treatment period", "title": "Unprocessable Content" }
```

Every state changing request needs the session cookie and the `X-XSRF-TOKEN` header (value of the `XSRF-TOKEN` cookie). Call `GET /api/auth/csrf` once before logging in.

## Authentication
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | `/auth/csrf` | anyone | Issue the CSRF cookie |
| POST | `/auth/login` | anyone | `{username, password}`; starts a session |
| POST | `/auth/logout` | signed in | End the session |
| GET | `/auth/me` | signed in | Current user |
| POST | `/auth/change-password` | signed in | `{currentPassword, newPassword}` |
| PUT | `/auth/contact` | signed in | `{email, mobile}` |

## Profile and organisation
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | `/profile` | EMPLOYEE | Own profile with dependents |
| PUT | `/profile/contact` | EMPLOYEE | Address and phones |
| POST | `/profile/dependents` | EMPLOYEE | Add a family member |
| DELETE | `/profile/dependents/{id}` | EMPLOYEE | Deactivate a family member |
| GET | `/org/dispensaries` | signed in | Dispensary list |
| GET | `/org/my-office` | signed in | Office shown in the header |
| GET | `/org/pao/schools` | PAO roles | Schools served by my PAO |

## Documents
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST | `/documents` | EMPLOYEE | Multipart `file` + `category`; returns metadata |
| GET | `/documents/{id}` | owner | Metadata |
| GET | `/documents/{id}/content` | owner | Download |

Reviewers download through `/claims/{id}/documents/{documentId}` and `/nac/{id}/prescription`, which check that the document belongs to a record in their scope.

## e-NAC
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST | `/nac` | EMPLOYEE | New request |
| GET | `/nac/mine` | EMPLOYEE | My requests |
| POST | `/nac/{id}/resubmit` | EMPLOYEE | Resubmit a returned request |
| GET | `/nac/{id}` | owner, dispensary staff | Detail |
| GET | `/nac/{id}/prescription` | owner, dispensary staff | Prescription file |
| GET | `/nac/queue` | PHARMACIST, MEDICAL_OFFICER | My stage's queue |
| POST | `/nac/queue/take-next` | PHARMACIST, MEDICAL_OFFICER | Take the oldest request |
| POST | `/nac/{id}/release` | holder | Put back in the queue |
| POST | `/nac/{id}/pharmacist-review` | PHARMACIST | `{decisions: [{itemId, decision, reason}], remarks}` |
| POST | `/nac/{id}/return` | PHARMACIST | `{remarks}` back to employee |
| POST | `/nac/{id}/countersign` | MEDICAL_OFFICER | `{password, remarks}`; issues the certificate |
| POST | `/nac/{id}/send-back` | MEDICAL_OFFICER | `{remarks}` back to the pharmacist |

## Claims
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | `/claims/meta` | signed in | Options, undertaking and certificate texts |
| GET | `/claims/mine` | EMPLOYEE | My claims |
| GET | `/claims/claimable-nac-items` | EMPLOYEE | e-NAC items not yet claimed |
| POST | `/claims` | EMPLOYEE | Create a draft |
| PUT | `/claims/{id}` | EMPLOYEE | Update a draft or returned claim |
| DELETE | `/claims/{id}` | EMPLOYEE | Delete a draft |
| POST | `/claims/{id}/submit` | EMPLOYEE | `{undertakingAccepted: true}` |
| POST | `/claims/{id}/withdraw` | EMPLOYEE | Withdraw |
| GET | `/claims/{id}` | owner, reviewers in scope | Full view with timeline, check list, `allowedActions` |
| GET | `/claims/{id}/documents/{documentId}` | owner, reviewers in scope | Bill, attachment or linked prescription |
| GET | `/claims/queue` | HOS, PAO_AUDITOR, PAO_OFFICER | My stage's queue |
| POST | `/claims/queue/take-next` | same | Take the oldest claim |
| GET | `/claims/office?status=` | same | History of my school or PAO |
| POST | `/claims/{id}/release` | holder | Put back in the queue |
| POST | `/claims/{id}/return` | HOS, PAO_AUDITOR | `{reasons: [...], remarks}` |
| POST | `/claims/{id}/forward` | HOS | `{items: [{itemId, dgehsRate, amountRestricted, remarks}], certificateAccepted, password, remarks}` |
| POST | `/claims/{id}/audit` | PAO_AUDITOR | `{items: [{itemId, amountAdmitted, disallowReason}], recommendation, remarks}` |
| POST | `/claims/{id}/sanction` | PAO_OFFICER | `{password, remarks}` |
| POST | `/claims/{id}/send-back` | PAO_OFFICER | `{remarks}` |
| POST | `/claims/{id}/reject` | PAO_OFFICER | `{password, reason}` |

## Budget
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | `/budget/school` | HOS | Position, demands, allocations |
| POST | `/budget/school/demand` | HOS | Raise the calculated demand |
| GET | `/budget/pao/schools` | PAO roles | Position of every school |
| GET | `/budget/pao/schools/{id}` | PAO roles | Allocations, payable queue, batches |
| POST | `/budget/pao/schools/{id}/allocations` | PAO_OFFICER | `{amount, sanctionOrderNo, remarks, financialYear}` |
| POST | `/budget/pao/schools/{id}/pay` | PAO_OFFICER | `{password}`; runs a payment batch |
| GET | `/budget/pao/demands` | PAO roles | Demands from schools |
| POST | `/budget/pao/demands/{id}/acknowledge` | PAO_OFFICER | Acknowledge |

## Dashboards and notifications
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | `/dashboard` | signed in | Role specific figures |
| GET | `/notifications` | signed in | Latest 50 |
| GET | `/notifications/unread-count` | signed in | Badge count |
| POST | `/notifications/{id}/read` | recipient | Mark read |
| POST | `/notifications/read-all` | signed in | Mark all read |

## Administration (ADMIN)
| Method | Path | Purpose |
|--------|------|---------|
| GET / POST | `/admin/users` | Search and create official accounts |
| POST | `/admin/users/{id}/reset-password` | Issue a temporary password |
| POST | `/admin/users/{id}/unlock`, `/disable`, `/enable` | Account state |
| GET / POST | `/admin/paos`, `/admin/schools`, `/admin/dispensaries` | Master data |
| GET / POST | `/admin/employees` | Search and onboard employees |
| PUT | `/admin/employees/{userId}` | Update service details |
| GET | `/admin/audit?entityType=&entityId=&actor=&action=&page=` | Search the audit trail |
| GET | `/admin/audit/verify` | Verify the hash chain |

## Common error codes
| Code | HTTP | Meaning |
|------|------|---------|
| `UNAUTHENTICATED` | 401 | No session |
| `SESSION_REPLACED` | 401 | Signed in elsewhere |
| `INVALID_CREDENTIALS` | 401 | Login failed (generic) |
| `CSRF_INVALID` | 403 | Missing or stale CSRF token |
| `PASSWORD_CHANGE_REQUIRED` | 403 | Temporary password must be changed |
| `FORBIDDEN` | 403 | Role not allowed |
| `NOT_FOUND` | 404 | Missing or out of scope |
| `VALIDATION_FAILED` | 400 | See `fields` |
| `INVALID_STATE` | 422 | Workflow rule (wrong stage, not the holder) |
| `CLAIM_INCOMPLETE` | 422 | Submission checks failed; `detail` lists all problems |
| `QUEUE_EMPTY` | 422 | Nothing to take |
| `PASSWORD_CONFIRMATION_FAILED` | 422 | Wrong password on a signature step |
| `CONCURRENT_UPDATE` | 409 | Someone else changed the record |
| `RATE_LIMITED` | 429 | Too many requests |
