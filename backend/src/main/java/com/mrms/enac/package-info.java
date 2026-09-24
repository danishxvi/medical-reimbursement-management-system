/**
 * e-NAC module: the electronic Non Availability Certificate.
 *
 * <p>The employee submits a prescription with its items to the dispensary
 * (AMA). The pharmacist records, item by item, whether it is available,
 * not available or not admissible. The Medical Officer In Charge reviews
 * and countersigns with a password confirmation, or sends it back to the
 * same pharmacist. Every decision is stored against the person who made it.
 */
@org.springframework.modulith.ApplicationModule(displayName = "e-NAC (dispensary)")
package com.mrms.enac;
