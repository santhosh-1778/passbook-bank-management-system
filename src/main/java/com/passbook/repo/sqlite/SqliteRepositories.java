package com.passbook.repo.sqlite;

import com.passbook.repo.Repositories;

/** Builds every SQLite-backed repository over one shared connection (Singleton-style: one Database per file). */
public final class SqliteRepositories {
    private SqliteRepositories() {
    }

    public static Repositories open(String path) {
        Database db = new Database(path);
        return new Repositories(new SqliteUnitOfWork(db), new SqliteUserRepository(db),
                new SqliteCustomerRepository(db), new SqliteAccountRepository(db),
                new SqliteTransactionRepository(db), new SqliteLoanRepository(db), new SqliteAuditRepository(db));
    }
}
