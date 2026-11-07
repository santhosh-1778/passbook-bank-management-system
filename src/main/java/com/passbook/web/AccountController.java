package com.passbook.web;

import com.passbook.exception.BankException;
import com.passbook.model.Permission;
import com.passbook.model.User;
import com.passbook.service.AccountDetails;
import com.passbook.service.AccountService;
import com.passbook.service.CustomerDetails;
import com.passbook.service.OpenAccountRequest;
import com.passbook.service.OpenedAccount;
import com.passbook.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Accounts, transfers, account opening and the customer register. */
public final class AccountController extends Controller {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public void register(Router r) {
        r.route("GET", "/account/{no}", this::show);
        r.route("POST", "/account/{no}/{action}", this::act);
        r.route("GET", "/transfer", Permission.SELF_SERVICE, this::transferForm);
        r.route("POST", "/transfer", Permission.SELF_SERVICE, this::doTransfer);
        r.route("GET", "/accounts/open", Permission.OPEN_ACCOUNT, this::openForm);
        r.route("POST", "/accounts/open", Permission.OPEN_ACCOUNT, this::doOpen);
        r.route("GET", "/customers", Permission.MANAGE_CUSTOMERS, this::customers);
        r.route("GET", "/customers/{id}", Permission.MANAGE_CUSTOMERS, this::customerForm);
        r.route("POST", "/customers/{id}", Permission.MANAGE_CUSTOMERS, this::saveCustomer);
    }

    private Response show(Request req) {
        User u = req.user();
        String no = req.param("no");
        AccountDetails d = accounts.details(u, no);
        String from = req.query("from"), to = req.query("to");
        List<com.passbook.model.Transaction> statement = null;
        if (!from.isEmpty() && !to.isEmpty()) {
            try {
                statement = accounts.statement(u, no, LocalDate.parse(from), LocalDate.parse(to));
            } catch (DateTimeParseException e) {
                flash(req, "err", "Enter valid dates.");
            } catch (BankException e) {
                flash(req, "err", e.getMessage());
            }
        }
        return Response.html(BankingViews.account(ctx(req), d, accounts.miniStatement(u, no), statement, from, to));
    }

    private Response act(Request req) {
        User u = req.user();
        String no = req.param("no");
        String action = req.param("action");
        accounts.details(u, no);  // 404 / 403 before doing anything
        switch (action) {
            case "deposit":
                attempt(req, () -> "Deposit recorded. New balance " + Money.format(accounts.deposit(u, no, Money.parse(req.form("amount")))) + ".");
                break;
            case "withdraw":
                attempt(req, () -> "Withdrawal recorded. New balance " + Money.format(accounts.withdraw(u, no, Money.parse(req.form("amount")))) + ".");
                break;
            case "close":
                attempt(req, () -> {
                    accounts.close(u, no);
                    return "Account closed.";
                });
                break;
            case "freeze":
            case "unfreeze":
                attempt(req, () -> {
                    accounts.setFrozen(u, no, action.equals("freeze"));
                    return "Account " + action + "d.";
                });
                break;
            default:
                return null;  // the server turns this into a 404
        }
        return Response.redirect("/account/" + no);
    }

    private Response transferForm(Request req) {
        User u = req.user();
        return Response.html(BankingViews.transfer(ctx(req), activeAccounts(u)));
    }

    private List<com.passbook.model.Account> activeAccounts(User u) {
        List<com.passbook.model.Account> out = new java.util.ArrayList<>();
        for (com.passbook.model.Account a : accounts.accountsOf(u, u.customerId())) {
            if (a.getStatus() == com.passbook.model.AccountStatus.ACTIVE) out.add(a);
        }
        return out;
    }

    private Response doTransfer(Request req) {
        User u = req.user();
        String from = req.form("from_no");
        boolean ok = attempt(req, () -> "Transfer complete. Source balance is now "
                + Money.format(accounts.transfer(u, from, req.form("to_no"), Money.parse(req.form("amount")))) + ".");
        return Response.redirect(ok ? "/account/" + from : "/transfer");
    }

    private Response openForm(Request req) {
        return Response.html(BankingViews.openAccount(ctx(req), accounts.searchCustomers(req.user(), ""), java.util.Map.of()));
    }

    private Response doOpen(Request req) {
        User u = req.user();
        try {
            String existing = req.form("customer_id");
            BigDecimal opening = req.form("opening").isEmpty() ? Money.ZERO : Money.parse(req.form("opening"));
            BigDecimal overdraft = req.form("overdraft").isEmpty() ? Money.ZERO : Money.parse(req.form("overdraft"));
            OpenedAccount o = accounts.openAccount(u, new OpenAccountRequest(req.form("type"), opening, overdraft,
                    existing.isEmpty() ? null : Long.valueOf(existing),
                    existing.isEmpty() ? new CustomerDetails(req.form("name"), req.form("address"), req.form("phone"), req.form("id_proof")) : null));
            flash(req, "ok", "Account " + o.accountNo() + " opened.");
            if (o.tempPassword() != null) {
                flash(req, "key", "Customer login created. User ID: " + o.loginId() + "   Temporary password: " + o.tempPassword() + "  (shown once)");
            }
            return Response.redirect("/account/" + o.accountNo());
        } catch (com.passbook.exception.AccessDeniedException e) {
            throw e;
        } catch (BankException | NumberFormatException e) {
            flash(req, "err", e instanceof BankException ? e.getMessage() : "Check the numbers you entered.");
            return Response.html(BankingViews.openAccount(ctx(req), accounts.searchCustomers(u, ""), req.formData()));
        }
    }

    private Response customers(Request req) {
        String q = req.query("q");
        return Response.html(BankingViews.customers(ctx(req), accounts.searchCustomers(req.user(), q), q));
    }

    private Response customerForm(Request req) {
        long id = req.paramLong("id");
        com.passbook.model.CustomerProfile p = accounts.customer(req.user(), id);
        java.util.Map<String, String> f = java.util.Map.of("name", p.name(), "address", p.address(), "phone", p.phone(), "id_proof", p.idProof());
        return Response.html(BankingViews.customerEdit(ctx(req), id, f, accounts.accountsOf(req.user(), id)));
    }

    private Response saveCustomer(Request req) {
        User u = req.user();
        long id = req.paramLong("id");
        accounts.customer(u, id);
        boolean ok = attempt(req, () -> {
            accounts.updateProfile(u, id, new CustomerDetails(req.form("name"), req.form("address"), req.form("phone"), req.form("id_proof")));
            return "Profile updated.";
        });
        if (ok) return Response.redirect("/customers/" + id);
        return Response.html(BankingViews.customerEdit(ctx(req), id, req.formData(), accounts.accountsOf(u, id)));
    }
}
