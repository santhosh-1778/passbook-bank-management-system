package com.passbook.web;

import com.passbook.model.Loan;
import com.passbook.model.Permission;
import com.passbook.model.User;
import com.passbook.service.AccountService;
import com.passbook.service.LoanService;
import com.passbook.util.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LoanController extends Controller {
    private final LoanService loans;
    private final AccountService accounts;

    public LoanController(LoanService loans, AccountService accounts) {
        this.loans = loans;
        this.accounts = accounts;
    }

    @Override
    public void register(Router r) {
        r.route("GET", "/loans", Permission.SELF_SERVICE, this::show);
        r.route("POST", "/loans", Permission.SELF_SERVICE, this::apply);
        r.route("POST", "/loans/{id}/repay", Permission.SELF_SERVICE, this::repay);
        r.route("GET", "/loans/review", Permission.APPROVE_LOAN, this::review);
        r.route("POST", "/loans/{id}/decide", Permission.APPROVE_LOAN, this::decide);
    }

    private List<com.passbook.model.Account> activeAccounts(User u) {
        List<com.passbook.model.Account> out = new ArrayList<>();
        for (com.passbook.model.Account a : accounts.accountsOf(u, u.customerId())) {
            if (a.getStatus() == com.passbook.model.AccountStatus.ACTIVE) out.add(a);
        }
        return out;
    }

    private Response page(Request req, Map<String, String> form) {
        User u = req.user();
        return Response.html(LoanViews.loans(ctx(req), activeAccounts(u), loans.myLoans(u), Loan.DEFAULT_RATE, form));
    }

    private Response show(Request req) {
        return page(req, Map.of());
    }

    private Response apply(Request req) {
        User u = req.user();
        boolean ok = attempt(req, () -> {
            LoanService.Application a = loans.apply(u, req.form("account_no"), Money.parse(req.form("amount")),
                    intField(req.form("tenure")), req.form("purpose"));
            return "Application #" + a.id() + " submitted. Your EMI would be " + Money.format(a.emi()) + ". A manager will review it.";
        });
        return ok ? Response.redirect("/loans") : page(req, req.formData());
    }

    private Response repay(Request req) {
        User u = req.user();
        long id = req.paramLong("id");
        attempt(req, () -> {
            java.math.BigDecimal left = loans.repay(u, id, req.form("account_no"), Money.parse(req.form("amount")));
            return left.signum() == 0 ? "Loan fully repaid." : "Payment received. " + Money.format(left) + " still outstanding.";
        });
        return Response.redirect("/loans");
    }

    private Response review(Request req) {
        return Response.html(LoanViews.review(ctx(req), loans.allLoans(req.user())));
    }

    private Response decide(Request req) {
        User u = req.user();
        long id = req.paramLong("id");
        boolean approve = "approve".equals(req.form("decision"));
        attempt(req, () -> {
            loans.decide(u, id, approve);
            return "Loan #" + id + (approve ? " approved and disbursed." : " rejected.");
        });
        return Response.redirect("/loans/review");
    }
}
