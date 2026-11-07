package com.passbook.repo;

/** One bundle holding every repository, so wiring code passes a single object around. */
public record Repositories(UnitOfWork uow, UserRepository users, CustomerRepository customers,
                           AccountRepository accounts, TransactionRepository transactions,
                           LoanRepository loans, AuditRepository audit) {
}
