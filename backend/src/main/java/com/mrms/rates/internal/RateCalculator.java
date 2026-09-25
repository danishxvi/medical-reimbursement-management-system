package com.mrms.rates.internal;

import com.mrms.rates.RateBasis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Works out the applicable rate from a list entry, following the CGHS
 * memorandum of 03.10.2025:
 * <ul>
 *   <li>the column is chosen by hospital basis (NABH, Non-NABH, super speciality);</li>
 *   <li>listed rates are for a semi private ward; for indoor packages a general ward
 *       entitlement is 5% lower and a private ward entitlement 5% higher;</li>
 *   <li>consultations, investigations, radiotherapy, day care and minor procedures
 *       not needing admission are the same for every ward.</li>
 * </ul>
 */
final class RateCalculator {

    private static final BigDecimal GENERAL = new BigDecimal("0.95");
    private static final BigDecimal PRIVATE = new BigDecimal("1.05");
    private static final List<String> UNIFORM = List.of("consultation", "investigation", "radiotherapy",
            "health check");

    record Result(BigDecimal base, BigDecimal applicable, String explanation) {
    }

    private RateCalculator() {
    }

    static Result apply(RateEntities.RateItem item, RateBasis basis, String ward, boolean indoor) {
        BigDecimal base = switch (basis) {
            case NABH -> item.getRateNabh();
            case NON_NABH -> item.getRateNonNabh();
            case SUPER_SPECIALITY -> item.getRateSuperSpeciality();
            case AS_BILLED -> throw new IllegalArgumentException("No rate applies to bills admissible as billed");
        };
        String column = switch (basis) {
            case NABH -> "NABH rate";
            case NON_NABH -> "Non-NABH rate";
            default -> "super speciality rate";
        };
        if (!indoor) {
            return new Result(base, base, column + "; out patient services are the same for every ward");
        }
        if (isUniform(item.getSpeciality())) {
            return new Result(base, base, column + "; " + item.getSpeciality().toLowerCase(Locale.ROOT)
                    + " is the same for every ward");
        }
        String entitlement = ward == null ? "SEMI_PRIVATE" : ward;
        return switch (entitlement) {
            case "GENERAL" -> new Result(base, money(base.multiply(GENERAL)),
                    column + " less 5% for general ward entitlement");
            case "PRIVATE" -> new Result(base, money(base.multiply(PRIVATE)),
                    column + " plus 5% for private ward entitlement");
            default -> new Result(base, base, column + " for semi private ward entitlement");
        };
    }

    static boolean isUniform(String speciality) {
        String s = speciality.toLowerCase(Locale.ROOT);
        return UNIFORM.stream().anyMatch(s::contains);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
