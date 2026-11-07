package com.passbook.tests;

import java.util.ArrayList;
import java.util.List;

/** A tiny test harness (no external libraries needed). */
public final class T {
    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    private T() {
    }

    public static void test(String name, Runnable body) {
        try {
            body.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (Throwable e) {
            failures.add(name + " -> " + e);
            System.out.println("  FAIL  " + name + "  (" + e + ")");
            if (!(e instanceof AssertionError)) e.printStackTrace(System.out);
        }
    }

    public static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void eq(Object expected, Object actual, String what) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void eqMoney(String expected, java.math.BigDecimal actual, String what) {
        if (actual == null || new java.math.BigDecimal(expected).compareTo(actual) != 0) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    public static void throwsA(Class<? extends Throwable> type, Runnable body, String what) {
        try {
            body.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) return;
            throw new AssertionError(what + ": expected " + type.getSimpleName() + " but got " + e);
        }
        throw new AssertionError(what + ": expected " + type.getSimpleName() + " but nothing was thrown");
    }

    public static int finish() {
        System.out.println();
        System.out.println(passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("  - " + f));
        return failures.isEmpty() ? 0 : 1;
    }
}
