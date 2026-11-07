package com.passbook.model;

/** FACTORY pattern for users. */
public final class UserFactory {
    private UserFactory() {
    }

    public static User restore(Role role, long id, String userId, String fullName, String passwordHash,
                               int failed, boolean locked, boolean active, Long customerId) {
        switch (role) {
            case CUSTOMER:
                return new Customer(id, userId, fullName, passwordHash, failed, locked, active, customerId);
            case TELLER:
                return new Teller(id, userId, fullName, passwordHash, failed, locked, active);
            case MANAGER:
                return new Manager(id, userId, fullName, passwordHash, failed, locked, active);
            default:
                throw new IllegalArgumentException("Unknown role " + role);
        }
    }

    public static User newEmployee(Role role, String userId, String fullName, String passwordHash) {
        return restore(role, 0, userId, fullName, passwordHash, 0, false, true, null);
    }
}
