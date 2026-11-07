package com.passbook.repo;

import com.passbook.model.CustomerProfile;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository {
    CustomerProfile insert(CustomerProfile profile);

    void update(CustomerProfile profile);

    Optional<CustomerProfile> findById(long id);

    List<CustomerProfile> search(String text);

    int count();
}
