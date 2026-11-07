package com.passbook.model;

public enum LoanStatus {
    APPLIED("Applied"), REJECTED("Rejected"), DISBURSED("Disbursed"), CLOSED("Closed");

    private final String label;

    LoanStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static LoanStatus fromLabel(String s) {
        for (LoanStatus v : values()) {
            if (v.label.equalsIgnoreCase(s)) return v;
        }
        throw new IllegalArgumentException("Unknown loan status: " + s);
    }
}
