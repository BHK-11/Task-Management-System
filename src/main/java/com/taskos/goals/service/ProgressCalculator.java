package com.taskos.goals.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProgressCalculator {
    private ProgressCalculator() { }

    public static BigDecimal fraction(BigDecimal baseline, BigDecimal target, BigDecimal current) {
        if (baseline == null || target == null || current == null || baseline.compareTo(target) == 0) return null;
        return current.subtract(baseline).divide(target.subtract(baseline), 8, RoundingMode.HALF_UP);
    }

    public static int displayPercent(BigDecimal fraction) {
        if (fraction == null) return 0;
        return fraction.max(BigDecimal.ZERO).min(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
