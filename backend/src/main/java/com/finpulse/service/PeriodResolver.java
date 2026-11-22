package com.finpulse.service;

import com.finpulse.exception.BusinessValidationException;

import java.time.YearMonth;

/**
 * Shared validator for optional {@code (month, year)} period query parameter pairs.
 */
final class PeriodResolver {

    private static final int MIN_YEAR = 2020;
    private static final int MAX_YEAR = 2100;

    private PeriodResolver() {
    }

    static YearMonth resolve(Integer month, Integer year) {
        if (month == null && year == null) {
            return YearMonth.now();
        }
        if (month == null || year == null) {
            throw new BusinessValidationException("Parameter 'month' dan 'year' harus diisi bersamaan");
        }
        if (month < 1 || month > 12) {
            throw new BusinessValidationException("Parameter 'month' harus antara 1 sampai 12");
        }
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BusinessValidationException("Parameter 'year' harus antara 2020 sampai 2100");
        }
        return YearMonth.of(year, month);
    }
}
