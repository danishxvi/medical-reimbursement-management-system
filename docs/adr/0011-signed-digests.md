# ADR 0011: Signatures cover a digest of the exact action, with Aadhaar eSign as a mode

**Status:** Accepted
**Author:** Danish Husain

## Context

Certificates, countersignatures, sanctions, rejections and payment releases were confirmed by re-entering a password. That proves who acted, but not what they approved, and a password is not a legally recognised electronic signature. Aadhaar eSign, provided by licensed eSign Service Providers under the IT Act, is. Using it requires the Directorate to register as an Application Service Provider, which a prototype cannot do, and the rest of the system must keep working in the meantime.

## Decision

1. A **signing module** offers one call, `confirm`, used by every signed action. It works in password mode or eSign mode, chosen by configuration.
2. Each signed action defines a **digest**: SHA-256 over a canonical text of exactly what is being approved. The owning module computes it before signing and again when the action runs; a mismatch refuses the action. A signature is consumed by one action.
3. In eSign mode the portal follows the CCA eSign API shape: signed request XML, signer authentication at the ESP, PKCS#7 over the digest, signed response. Responses are verified against configured ESP certificates only, and signer certificates against configured trusted CAs.
4. The Aadhaar number never reaches the portal.
5. A **built in ESP simulator** (dev and test profiles only) lets the full flow run in development and in automated tests.

## Consequences

- The legal strength of a signature is a configuration change once the ASP registration exists; no code changes.
- Signatures are bound to content, so a signed certificate cannot be applied to a changed calculation sheet, and a signed payment run cannot pay a different set of claims.
- Signing takes a few more clicks in eSign mode, and depends on the ESP being available; password mode remains as a fallback the Directorate can switch to.
- Provider specific details (form field names, API version attributes) must be checked against the chosen ESP's kit before go live.
