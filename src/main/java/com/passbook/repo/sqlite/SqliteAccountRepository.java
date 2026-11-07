package com.passbook.repo.sqlite;

import com.passbook.model.Account;
import com.passbook.model.AccountFactory;
import com.passbook.model.AccountStatus;
import com.passbook.model.CurrentAccount;
import com.passbook.model.SavingsAccount;
import com.passbook.repo.AccountRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

class SqliteAccountRepository implements AccountRepository {
    private static final long BASE = 5_000_100_000L;
    private static final String COLS =
            "SELECT account_no, customer_id, type, balance, min_balance, interest_rate, overdraft_limit, status, open_date FROM accounts";

    private final Database db;

    SqliteAccountRepository(Database db) {
        this.db = db;
    }

    private static Account map(ResultSet rs) throws SQLException {
        double rate = rs.getDouble("interest_rate");
        boolean noRate = rs.wasNull();  // must be checked right after reading that column
        return AccountFactory.restore(rs.getString("type"), rs.getString("account_no"), rs.getLong("customer_id"),
                Database.money(rs, "balance"), AccountStatus.fromLabel(rs.getString("status")),
                LocalDate.parse(rs.getString("open_date")), Database.moneyOrNull(rs, "overdraft_limit"),
                Database.moneyOrNull(rs, "min_balance"), noRate ? SavingsAccount.DEFAULT_RATE : rate);
    }

    @Override
    public String nextAccountNumber() {
        long seq = db.queryOne("SELECT COALESCE(MAX(seq), 0) + 1 FROM accounts", rs -> rs.getLong(1)).orElse(1L);
        return String.valueOf(BASE + seq);
    }

    @Override
    public void insert(Account a) {
        long seq = Long.parseLong(a.getAccountNo()) - BASE;
        BigDecimal min = null, overdraft = null;
        Double rate = null;
        if (a instanceof SavingsAccount) {
            min = ((SavingsAccount) a).getMinBalance();
            rate = ((SavingsAccount) a).annualRatePercent();
        } else if (a instanceof CurrentAccount) {
            overdraft = ((CurrentAccount) a).getOverdraftLimit();
        }
        db.insert("INSERT INTO accounts(seq, account_no, customer_id, type, balance, min_balance, interest_rate, overdraft_limit, status, open_date) VALUES(?,?,?,?,?,?,?,?,?,?)",
                seq, a.getAccountNo(), a.getCustomerId(), a.type(), a.getBalance(), min, rate, overdraft,
                a.getStatus().label(), a.getOpenDate());
    }

    @Override
    public void update(Account a) {
        db.update("UPDATE accounts SET balance = ?, status = ? WHERE account_no = ?",
                a.getBalance(), a.getStatus().label(), a.getAccountNo());
    }

    @Override
    public Optional<Account> findByNo(String accountNo) {
        return db.queryOne(COLS + " WHERE account_no = ?", SqliteAccountRepository::map, accountNo);
    }

    @Override
    public List<Account> findByCustomer(long customerId) {
        return db.query(COLS + " WHERE customer_id = ? ORDER BY seq", SqliteAccountRepository::map, customerId);
    }

    @Override
    public List<Account> findAllOpen() {
        return db.query(COLS + " WHERE status != 'Closed' ORDER BY seq", SqliteAccountRepository::map);
    }
}
