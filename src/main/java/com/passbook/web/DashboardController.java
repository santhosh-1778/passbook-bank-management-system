package com.passbook.web;

import com.passbook.model.Permission;
import com.passbook.model.User;
import com.passbook.service.AccountService;
import com.passbook.service.AdminService;
import com.passbook.service.LoanService;

import java.util.List;

public final class DashboardController extends Controller {
    private final AccountService accounts;
    private final LoanService loans;
    private final AdminService admin;

    public DashboardController(AccountService accounts, LoanService loans, AdminService admin) {
        this.accounts = accounts;
        this.loans = loans;
        this.admin = admin;
    }

    @Override
    public void register(Router r) {
        r.route("GET", "/", this::home);
    }

    private Response home(Request req) {
        User u = req.user();
        ViewContext c = ctx(req);
        if (u.can(Permission.SELF_SERVICE)) {
            return Response.html(BankingViews.customerDashboard(c, accounts.ownProfile(u),
                    accounts.accountsOf(u, u.customerId()), loans.myLoans(u)));
        }
        boolean manager = u.can(Permission.APPROVE_LOAN);
        return Response.html(BankingViews.staffDashboard(c, admin.stats(u), admin.recentTransactions(u),
                manager ? loans.pending(u) : List.of(), manager ? admin.lockedUsers(u) : List.of()));
    }
}
