package com.mrms.shared.domain;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Indian financial year (1 April to 31 March), formatted as "2026-27".
 */
public final class FinancialYear {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private FinancialYear() {
    }

    public static String of(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        int endYearShort = (startYear + 1) % 100;
        return startYear + "-" + String.format("%02d", endYearShort);
    }

    public static String current() {
        return of(LocalDate.now(IST));
    }

    /** Accepts only the canonical "YYYY-YY" form with consecutive years. */
    public static boolean isValid(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}")) {
            return false;
        }
        int start = Integer.parseInt(value.substring(0, 4));
        int end = Integer.parseInt(value.substring(5));
        return (start + 1) % 100 == end;
    }
}
