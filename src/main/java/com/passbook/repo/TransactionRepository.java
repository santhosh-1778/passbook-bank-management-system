package com.passbook.repo;

import com.passbook.model.Transaction;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository {
    void insert(Transaction t);

    List<Transaction> latest(String accountNo, int n);

    List<Transaction> between(String accountNo, LocalDate from, LocalDate to);

    List<Transaction> recent(int n);
}
