package com.mark43.loyalty.domain.entity;

import java.math.BigDecimal;

public enum Tier {

    SILVER(new BigDecimal("0.00")),
    GOLD(new BigDecimal("1000.00")),
    PLATINUM(new BigDecimal("2000.00"));

    private final BigDecimal spendThreshold;

    Tier(BigDecimal spendThreshold) {
        this.spendThreshold = spendThreshold;
    }

    public BigDecimal getSpendThreshold() {
        return this.spendThreshold;
    }

    public static Tier determineTierFromSpend(BigDecimal activeSpend) {
        if (activeSpend == null || activeSpend.compareTo(GOLD.spendThreshold) < 0) {
            return SILVER;
        }
        if (activeSpend.compareTo(PLATINUM.spendThreshold) < 0) {
            return GOLD;
        }
        return PLATINUM;
    }
}