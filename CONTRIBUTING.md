# Contributing

Maintainer: Danish Husain

## Ground rules

1. **Security first.** Never weaken an authorisation, validation or audit rule to make a feature easier. New endpoints need a role rule, an object level check in the service and a test.
2. **Respect module boundaries.** Use another module only through its public API (base package). `mvn verify` fails otherwise.
3. **Workflow rules belong in the aggregate** (`Claim`, `NacRequest`), not in controllers.
4. **Every state change is audited** and, for claims, written to the timeline.
5. **No secrets in the repository.** Configuration comes from the environment; see `.env.example`.

## Workflow

1. Create a branch from `main`.
2. Make the change with tests.
3. Run `mvn verify` in `backend` and `npm run lint && npm run build` in `frontend`.
4. Open a pull request describing the problem, the change and how it was tested. CI must be green.

## Style

- Java: 4 space indentation, constructor injection, package private classes inside `internal`, records for DTOs.
- TypeScript: strict mode, function components, no `any`.
- UI: use the components in `frontend/src/components/ui` and the tokens in `styles/tokens.css`; keep the saffron and white, rounded box visual language (one theme only); every new element gets a subtle animation that respects reduced motion.
- Text: plain language; avoid long dashes, use commas, colons or brackets instead.
- Commits: imperative subject line under 72 characters, body explaining why.
