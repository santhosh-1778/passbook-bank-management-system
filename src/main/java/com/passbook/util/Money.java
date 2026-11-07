package com.passbook.util;

import com.passbook.exception.BankException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

/** All money is a BigDecimal with 2 decimal places; never a double. */
public final class Money {
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    public static final BigDecimal MAX_SINGLE = new BigDecimal("10000000.00");
    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,12}(\\.\\d{1,6})?");

    private Money() {
    }

    /** Parses user input such as "1,500.5". Rejects anything that is not a plain number. */
    public static BigDecimal parse(String text) {
        String s = text == null ? "" : text.replace(",", "").trim();
        if (!NUMBER.matcher(s).matches()) {
            throw new BankException("Enter a valid amount");
        }
        return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal round(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal ofDouble(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** Business validation for a transaction amount. */
    public static BigDecimal validatedAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BankException("Amount must be a number");
        }
        BigDecimal a = round(amount);
        if (a.signum() <= 0) {
            throw new BankException("Amount must be positive");
        }
        if (a.compareTo(MAX_SINGLE) > 0) {
            throw new BankException("Amount exceeds the single-transaction limit of 10,000,000.00");
        }
        return a;
    }

    public static String format(BigDecimal v) {
        return String.format(Locale.US, "%,.2f", v == null ? ZERO : v);
    }
}
