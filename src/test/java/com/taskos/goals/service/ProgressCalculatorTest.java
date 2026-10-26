package com.taskos.goals.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProgressCalculatorTest {
    @Test
    void calculatesIncreasingAndDecreasingProgressAndClampsOnlyTheDisplay() {
        assertEquals(0, ProgressCalculator.displayPercent(ProgressCalculator.fraction(
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("8"))));
        assertEquals(50, ProgressCalculator.displayPercent(ProgressCalculator.fraction(
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("15"))));
        assertEquals(75, ProgressCalculator.displayPercent(ProgressCalculator.fraction(
                new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("4"))));
        assertEquals(100, ProgressCalculator.displayPercent(ProgressCalculator.fraction(
                new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("0"))));
        assertNull(ProgressCalculator.fraction(null, BigDecimal.ONE, BigDecimal.TEN));
        assertNull(ProgressCalculator.fraction(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN));
    }
}
