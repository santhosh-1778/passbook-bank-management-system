package com.passbook.model;

public enum Role {
    CUSTOMER, TELLER, MANAGER;

    public String label() {
        return name().toLowerCase();
    }

    public static Role fromLabel(String s) {
        return valueOf(s.toUpperCase());
    }
}
