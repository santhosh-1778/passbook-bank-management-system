package com.passbook.model;

import java.math.BigDecimal;

/** Interface: anything that earns interest. Only savings accounts implement it. */
public interface InterestBearing {
    BigDecimal monthlyInterest();

    double annualRatePercent();
}
