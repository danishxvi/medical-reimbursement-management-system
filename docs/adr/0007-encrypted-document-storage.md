# ADR 0007: Encrypted file storage with content based validation

**Status:** Accepted
**Author:** Danish Husain

## Context

Prescriptions and bills are sensitive health documents uploaded by thousands of users. Uploads are a classic attack vector.

## Decision

- Accept only PDF, JPEG and PNG, detected from the file content; the extension must match; PDFs with scripts, launch actions, embedded files or XFA are rejected; 5 MB limit.
- Store files under random names outside any web root, encrypted with AES-256-GCM; the file name is bound as associated data.
- Serve downloads only after a record level access check, as attachments with `nosniff` and a sandbox CSP; log every view.

## Consequences

- A stolen disk or backup reveals nothing without the key; the key must be managed and backed up separately.
- Compressed PDF object streams are not inspected; an antivirus scan at the gateway is recommended for production.
