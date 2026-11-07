package com.passbook.security;

/** Abstraction over password hashing, so the algorithm can be swapped without touching the services. */
public interface PasswordHasher {
    String hash(String password);

    boolean verify(String password, String stored);
}
