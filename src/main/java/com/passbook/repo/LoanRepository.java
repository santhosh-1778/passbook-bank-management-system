package com.passbook.repo;

import com.passbook.model.Loan;

import java.util.List;
import java.util.Optional;

public interface LoanRepository {
    void insert(Loan loan);

    void update(Loan loan);

    Optional<Loan> findById(long id);

    List<Loan> findByCustomer(long customerId);

    List<Loan> findAll();

    boolean hasActiveLoanOn(String accountNo);
}
