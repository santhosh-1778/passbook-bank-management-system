package com.passbook.service;

import com.passbook.model.Customer;
import com.passbook.model.CustomerProfile;
import com.passbook.model.Role;
import com.passbook.model.User;
import com.passbook.model.UserFactory;
import com.passbook.repo.Repositories;
import com.passbook.security.PasswordHasher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fills an empty database with one manager, one teller and one customer with two accounts. */
public final class DemoData {
    private DemoData() {
    }

    public static void seedIfEmpty(Repositories r, PasswordHasher hasher, AccountService accountService) {
        if (!r.users().isEmpty()) return;
        User manager = UserFactory.newEmployee(Role.MANAGER, "admin", "Meera Kapoor", hasher.hash("admin123"));
        r.users().insert(manager);
        r.users().insert(UserFactory.newEmployee(Role.TELLER, "teller1", "Rohan Das", hasher.hash("teller123")));
        CustomerProfile alice = r.customers().insert(new CustomerProfile(0, "Alice Verma", "12 Civil Lines, Prayagraj",
                "9876543210", "Aadhaar XXXX-4821", LocalDateTime.now().withNano(0)));
        r.users().insert(new Customer(0, "alice", alice.name(), hasher.hash("alice123"), 0, false, true, alice.id()));
        accountService.openAccount(manager, new OpenAccountRequest("savings", new BigDecimal("25000"), null,
                alice.id(), null));
        accountService.openAccount(manager, new OpenAccountRequest("current", new BigDecimal("8000"),
                new BigDecimal("5000"), alice.id(), null));
    }
}
