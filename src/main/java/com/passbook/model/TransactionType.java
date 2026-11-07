package com.passbook.model;

public enum TransactionType {
    DEPOSIT(true), WITHDRAW(false), TRANSFER_IN(true), TRANSFER_OUT(false),
    INTEREST(true), LOAN_DISBURSE(true), LOAN_REPAY(false);

    private final boolean credit;

    TransactionType(boolean credit) {
        this.credit = credit;
    }

    public boolean isCredit() {
        return credit;
    }

    /** "TRANSFER_IN" becomes "Transfer In". */
    public String label() {
        StringBuilder sb = new StringBuilder();
        for (String w : name().split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.charAt(0)).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
