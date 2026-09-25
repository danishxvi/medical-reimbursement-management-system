package com.mrms.rates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Public API of the rates module. */
public interface RateLookup {

    /**
     * The approved rate for a code on a date, adjusted for the hospital basis
     * and, for indoor packages, the beneficiary's ward entitlement.
     *
     * @param wardEntitlement GENERAL, SEMI_PRIVATE or PRIVATE (null is treated as semi private)
     * @return empty when the basis is AS_BILLED, the code is unknown, or no
     *         active list covers the date
     */
    Optional<RateQuote> quote(String code, LocalDate serviceDate, RateBasis basis, String wardEntitlement,
                              boolean indoor);

    /**
     * @param reference   compact reference stored with the claim item, for example CGHS-2025-T1/LB012/NABH
     * @param explanation how the applicable rate was worked out, in plain words
     */
    record RateQuote(
            String code,
            String name,
            String speciality,
            String listCode,
            String listTitle,
            RateBasis basis,
            BigDecimal baseRate,
            BigDecimal applicableRate,
            String reference,
            String explanation) {
    }
}
