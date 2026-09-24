# Security Policy

MRMS handles personal and health related information of government employees. Security reports are taken seriously.

## Reporting a vulnerability

- **Do not** open a public GitHub issue for security problems.
- Use GitHub's private vulnerability reporting ("Security" tab, "Report a vulnerability") on this repository.
- Include the affected component, steps to reproduce, and the impact you observed.

You can expect an acknowledgement within 3 working days and a status update within 10 working days.

## Supported versions

Only the latest commit on `main` is supported while the project is pre release.

## Scope

In scope: the backend API, the frontend application, the deployment configuration in this repository.
Out of scope: denial of service by volume, social engineering, findings that need a compromised administrator account.

The security design is documented in [docs/06-security.md](docs/06-security.md).
