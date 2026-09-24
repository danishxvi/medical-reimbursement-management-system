/**
 * Budget module.
 *
 * <p>Claim processing does not wait for money. Only payment does. The
 * school's budget demand is calculated automatically from the claims that
 * are actually in the pipeline, the PAO records allocations, and payment
 * runs pay sanctioned claims strictly oldest first while the balance lasts.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Budget and payments")
package com.mrms.budget;
