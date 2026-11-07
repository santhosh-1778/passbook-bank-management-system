package com.passbook.repo.sqlite;

/** The database design (tables, keys, constraints). Run on every start; IF NOT EXISTS keeps it idempotent. */
final class Schema {
    private Schema() {
    }

    static final String[] STATEMENTS = {
            """
            CREATE TABLE IF NOT EXISTS customers(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL, address TEXT NOT NULL, phone TEXT NOT NULL, id_proof TEXT NOT NULL,
              created_at TEXT NOT NULL)
            """,
            """
            CREATE TABLE IF NOT EXISTS users(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              user_id TEXT UNIQUE NOT NULL, full_name TEXT NOT NULL, password_hash TEXT NOT NULL,
              role TEXT NOT NULL CHECK(role IN ('customer','teller','manager')),
              customer_id INTEGER REFERENCES customers(id),
              failed_attempts INTEGER NOT NULL DEFAULT 0, locked INTEGER NOT NULL DEFAULT 0,
              active INTEGER NOT NULL DEFAULT 1)
            """,
            """
            CREATE TABLE IF NOT EXISTS accounts(
              seq INTEGER PRIMARY KEY AUTOINCREMENT,
              account_no TEXT UNIQUE NOT NULL,
              customer_id INTEGER NOT NULL REFERENCES customers(id),
              type TEXT NOT NULL CHECK(type IN ('savings','current')),
              balance REAL NOT NULL DEFAULT 0,
              min_balance REAL, interest_rate REAL, overdraft_limit REAL,
              status TEXT NOT NULL DEFAULT 'Active' CHECK(status IN ('Active','Frozen','Closed')),
              open_date TEXT NOT NULL)
            """,
            """
            CREATE TABLE IF NOT EXISTS transactions(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              account_no TEXT NOT NULL REFERENCES accounts(account_no),
              type TEXT NOT NULL, amount REAL NOT NULL, balance_after REAL NOT NULL,
              note TEXT, created_at TEXT NOT NULL)
            """,
            "CREATE INDEX IF NOT EXISTS ix_txn_acc ON transactions(account_no, id)",
            """
            CREATE TABLE IF NOT EXISTS loans(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              customer_id INTEGER NOT NULL REFERENCES customers(id),
              account_no TEXT NOT NULL REFERENCES accounts(account_no),
              principal REAL NOT NULL, rate REAL NOT NULL, tenure_months INTEGER NOT NULL,
              purpose TEXT NOT NULL, emi REAL NOT NULL, outstanding REAL NOT NULL DEFAULT 0,
              status TEXT NOT NULL CHECK(status IN ('Applied','Rejected','Disbursed','Closed')),
              applied_at TEXT NOT NULL, decided_by TEXT, decided_at TEXT)
            """,
            """
            CREATE TABLE IF NOT EXISTS audit_log(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              actor TEXT NOT NULL, action TEXT NOT NULL, detail TEXT, created_at TEXT NOT NULL)
            """
    };
}
