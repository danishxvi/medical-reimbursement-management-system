# 17. Testing

**Author:** Danish Husain

| Suite | Where | Runs | What it proves |
|-------|-------|------|----------------|
| Backend unit and integration | `backend/src/test` | `mvn verify` | Workflow rules, security (sessions, CSRF, lockout, headers, cookies), module boundaries, audit chain, file checks, virus scan protocol, naming, rates, PDF, eSign (with the simulator, forged responses, changed content, reuse), deadlines and escalation, outbox |
| Frontend unit and component | `frontend/src/**/*.test.ts(x)` | `npm test` (Vitest, jsdom) | Onboarding redirect order, both signing modes and the payload they sign, rate code search, guides for every role, document names and scan marks, formatting |
| End to end | `frontend/e2e` | `npm run e2e` (Playwright) | Real journeys in a real browser against a real API: public pages and licence notice, first sign in (privacy notice, guide), oversight landing, and an Aadhaar eSign countersignature through the signing window |

## Isolation

- Every backend Spring test class gets its own freshly seeded in memory database (a random database name per context, and the context is closed after the class). Tests never depend on the order they run in; CI on Linux and development on Windows run them in different orders.
- The end to end suite starts its own API on port 8090 (dev profile, in memory database, eSign simulator) and its own Vite server on 5174, so it never touches development data and can run beside a running development setup.

## Running

```bash
cd backend && mvn verify
```

```bash
cd frontend && npm test
```

```bash
cd frontend && npx playwright install chromium && npm run e2e
```

On a machine with Microsoft Edge or Google Chrome, `PW_CHANNEL=msedge npm run e2e` uses the installed browser instead of downloading one.

CI runs all three suites on every push, together with lint, type checks, the production build, a dependency audit and CodeQL.
