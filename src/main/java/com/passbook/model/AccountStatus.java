package com.passbook.model;

public enum AccountStatus {
    ACTIVE("Active"), FROZEN("Frozen"), CLOSED("Closed");

    private final String label;

    AccountStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static AccountStatus fromLabel(String s) {
        for (AccountStatus v : values()) {
            if (v.label.equalsIgnoreCase(s)) return v;
        }
        throw new IllegalArgumentException("Unknown account status: " + s);
    }
}
