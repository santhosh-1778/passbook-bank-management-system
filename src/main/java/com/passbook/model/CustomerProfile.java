package com.passbook.model;

import com.passbook.exception.BankException;

import java.time.LocalDateTime;

/** Personal details of a bank customer. The record validates itself, so an invalid profile cannot exist. */
public record CustomerProfile(long id, String name, String address, String phone, String idProof,
                              LocalDateTime createdAt) {
    public CustomerProfile {
        name = required(name, "Name", 80);
        address = required(address, "Address", 200);
        phone = validPhone(phone);
        idProof = required(idProof, "ID proof", 60);
    }

    public CustomerProfile withId(long newId) {
        return new CustomerProfile(newId, name, address, phone, idProof, createdAt);
    }

    private static String required(String v, String label, int max) {
        String s = v == null ? "" : v.trim();
        if (s.isEmpty()) throw new BankException(label + " is required");
        if (s.length() > max) throw new BankException(label + " is too long");
        return s;
    }

    private static String validPhone(String v) {
        String s = required(v, "Phone", 30);
        String digits = s.replace(" ", "").replace("-", "");
        if (digits.startsWith("+")) digits = digits.substring(1);
        if (!digits.matches("\\d{7,15}")) throw new BankException("Phone must contain 7 to 15 digits");
        return s;
    }
}
