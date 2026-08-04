package com.sporty.jackpot.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary rounding rules for the whole service — 2 decimals, HALF_UP — and the shared checks that
 * keep percentages and amounts sane.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal scaled(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** {@code amount * percentage / 100}, rounded to the monetary scale. */
    public static BigDecimal percentageOf(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal requirePercentage(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(name + " must be a percentage between 0 and 100");
        }
        return value;
    }

    public static BigDecimal requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public static BigDecimal requireNotNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
