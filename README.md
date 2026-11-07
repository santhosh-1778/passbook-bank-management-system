# Passbook: Bank Management System (OOM project, Java + SQLite)

A web application written in **plain Java 17** (no framework) with **SQLite** storage and a hand-written CSS design.
It implements the requirements, use cases and class diagram of the project guide (FR1-FR19).

## Run it

You need JDK 17 or newer (`java -version`).

**Option A: Maven**
    mvn -q compile exec:java

**Option B: no Maven** (downloads the two jars the first time)
    ./run.sh          (Windows: run.bat)

Open http://127.0.0.1:8080 . The file `bank.db` is created on first start with demo data.
Change the port with the PORT environment variable, the database file with BANK_DB.

| Role     | User ID | Password  |
|----------|---------|-----------|
| Customer | alice   | alice123  |
| Teller   | teller1 | teller123 |
| Manager  | admin   | admin123  |

## Tests
    ./test.sh         (Windows: test.bat)
31 tests, no database needed. They cover TC1-TC9 of the report, loans, interest, rollback, concurrency,
and the web layer over real HTTP (login, CSRF, roles, XSS escaping).
`python3 scripts/check_sql.py` checks every SQL statement against a real SQLite engine.

## OOP concepts and where to find them

| Concept | Where |
|---|---|
| **Abstraction** | `Account` (abstract: `canWithdraw`, `type`, `termsSummary`), `User` (abstract: `role`, `permissions`), interfaces `InterestBearing`, `PasswordHasher`, every `*Repository`, `UnitOfWork` |
| **Encapsulation** | `Account.balance` is private; it changes only via `deposit()` / `withdraw()`. `User` lockout state changes only via `registerFailedAttempt()` / `unlock()`. `Loan` owns its state transitions |
| **Inheritance** | `SavingsAccount`, `CurrentAccount` extend `Account`. `Customer`, `Teller` extend `User`; `Manager extends Teller`. All exceptions extend `BankException`. Controllers extend `Controller` |
| **Polymorphism** | `canWithdraw()` (min balance vs overdraft), `permissions()`, `termsSummary()`, `copy()`; one `List<Controller>` registers every route; services use `Account` / `User` references only |
| **Interfaces** | `InterestBearing` (only savings), `Repository` interfaces with SQLite and in-memory implementations |
| **Method overriding / overloading** | `Manager.permissions()` extends `Teller.permissions()`; `Response.html(...)`, `Router.route(...)` overloaded |
| **Exception handling** | `InsufficientFundsException`, `AccountNotFoundException`, `InvalidLoginException`, `AccessDeniedException`; the transfer rolls back on any failure |
| **Composition / aggregation** | `AccountService` is composed of repositories, the event bus and the hasher |
| **Static / final / enums / records** | `Money`, `AccountFactory`; `final` template methods; `Role`, `Permission`, `AccountStatus`, `TransactionType`; records `Transaction`, `CustomerProfile` |
| **Generics / lambdas** | `UnitOfWork.call(Supplier<T>)`, `Database.query(RowMapper<T>)`, `Handler` lambdas |

Design patterns: **Factory** (`AccountFactory`, `UserFactory`), **Template Method** (`Account.withdraw`),
**Observer** (`EventBus` + `AuditListener`), **Repository** and **Unit of Work** (storage hidden behind interfaces),
**Prototype** (`copy()`), **Dependency Injection** (wired by hand in `App`), **MVC** (`web/*Controller`, `*Views`, services/model).

## Package map
    com.passbook.model       Account, SavingsAccount, CurrentAccount, User, Customer, Teller, Manager, Loan, ...
    com.passbook.exception   BankException and its subclasses
    com.passbook.repo        repository interfaces (+ repo.sqlite = JDBC implementations)
    com.passbook.service     AuthService, AccountService, LoanService, AdminService, EventBus
    com.passbook.security    PasswordHasher, Pbkdf2PasswordHasher (salted PBKDF2)
    com.passbook.web         BankServer, Router, controllers, views, sessions, CSRF
    com.passbook.App         main(): wires everything

## Notes
* Loan approval pays out in one step; interest is credited by a manager button; loan rate is fixed at 10% p.a.
* The "Dormant" state from the state diagram is not automated. Single branch, single currency.
* All database access goes through one connection guarded by a lock, so concurrent requests cannot corrupt balances.
* For real deployment, serve behind HTTPS.
