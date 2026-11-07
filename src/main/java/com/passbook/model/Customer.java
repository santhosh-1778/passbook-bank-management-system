package com.passbook.model;

import java.util.EnumSet;
import java.util.Set;

public class Customer extends User {
    private final long customerId;

    public Customer(long id, String userId, String fullName, String passwordHash, int failed, boolean locked,
                    boolean active, long customerId) {
        super(id, userId, fullName, passwordHash, failed, locked, active);
        this.customerId = customerId;
    }

    @Override public Role role() { return Role.CUSTOMER; }

    @Override public Set<Permission> permissions() { return EnumSet.of(Permission.SELF_SERVICE); }

    @Override public Long customerId() { return customerId; }

    @Override
    public User copy() {
        return new Customer(getId(), getUserId(), getFullName(), getPasswordHash(), getFailedAttempts(),
                isLocked(), isActive(), customerId);
    }
}
