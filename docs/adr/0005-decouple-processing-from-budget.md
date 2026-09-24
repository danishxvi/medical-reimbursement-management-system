# ADR 0005: Decouple claim processing from budget arrival

**Status:** Accepted
**Author:** Danish Husain

## Context

Today a claim cannot move until the school's budget arrives, and a returned claim waits for the next budget. Budget demand is collected as verbal estimates.

## Decision

- Claims are verified, scrutinised and sanctioned whenever they arrive. Only payment needs funds.
- The school's demand is computed by the server from claims actually in the pipeline minus the balance; the HoS cannot type an arbitrary figure.
- Payment runs pay sanctioned claims strictly oldest first and stop at the first claim the balance cannot cover, so a small new claim never overtakes an old one.

## Consequences

- No claim waits for money before it is checked; corrections never cost a budget cycle.
- Demand reflects real need, reducing both lapsed funds and shortfalls.
- Payment is recorded in MRMS with a batch reference; integration with the treasury system is a later release.
