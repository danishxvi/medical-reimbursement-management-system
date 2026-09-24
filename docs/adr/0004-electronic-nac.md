# ADR 0004: Item level electronic NAC with named decisions

**Status:** Accepted
**Author:** Danish Husain

## Context

On paper, the dispensary stamps the whole prescription and it is later hard to say who decided what. Wrong certificates are issued and responsibility is avoided.

## Decision

Model the non availability certificate as its own aggregate with one decision per prescribed item (available, not available, not admissible with a reason). Each decision stores the pharmacist and the time. The Medical Officer countersigns with password confirmation or sends it back to the same pharmacist. Claims link each medicine bill to a specific not available item, and an item can be claimed only once.

## Consequences

- Clear accountability per item; the PAO sees the evidence next to the bill.
- A paper NAC is still accepted during transition, but the item is flagged as legacy for extra scrutiny.
- Dispensaries need accounts and a simple queue screen, which is part of this release.
