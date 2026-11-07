package com.passbook.repo;

import com.passbook.model.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    String nextAccountNumber();

    void insert(Account account);

    void update(Account account);

    Optional<Account> findByNo(String accountNo);

    List<Account> findByCustomer(long customerId);

    /** Every account that is not closed. */
    List<Account> findAllOpen();
}
