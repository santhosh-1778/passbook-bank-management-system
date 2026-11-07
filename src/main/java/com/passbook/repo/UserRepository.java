package com.passbook.repo;

import com.passbook.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(long id);

    Optional<User> findByUserId(String userId);

    Optional<User> findByCustomerId(long customerId);

    /** Stores a new user and assigns its id. */
    void insert(User user);

    void update(User user);

    List<User> findEmployees();

    List<User> findLocked();

    boolean isEmpty();
}
