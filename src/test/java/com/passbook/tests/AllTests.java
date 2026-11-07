package com.passbook.tests;

public final class AllTests {
    private AllTests() {
    }

    public static void main(String[] args) {
        BankTests.run();
        WebTests.run();
        System.exit(T.finish());
    }
}
