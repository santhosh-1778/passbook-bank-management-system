package com.passbook.web;

import com.passbook.model.Permission;
import com.passbook.model.Role;
import com.passbook.model.User;
import com.passbook.service.AccountService;
import com.passbook.service.AdminService;

public final class AdminController extends Controller {
    private final AdminService admin;
    private final AccountService accounts;

    public AdminController(AdminService admin, AccountService accounts) {
        this.admin = admin;
        this.accounts = accounts;
    }

    @Override
    public void register(Router r) {
        r.route("GET", "/users", Permission.MANAGE_STAFF, this::showUsers);
        r.route("POST", "/users", Permission.MANAGE_STAFF, this::createUser);
        r.route("POST", "/users/{id}/{action}", Permission.MANAGE_STAFF, this::userAction);
        r.route("POST", "/interest", Permission.CREDIT_INTEREST, this::interest);
        r.route("GET", "/audit", Permission.VIEW_AUDIT, this::audit);
    }

    private Response showUsers(Request req) {
        User u = req.user();
        return Response.html(AdminViews.users(ctx(req), admin.employees(u), admin.lockedUsers(u)));
    }

    private Response createUser(Request req) {
        User u = req.user();
        attempt(req, () -> {
            Role role;
            try {
                role = Role.fromLabel(req.form("role"));
            } catch (IllegalArgumentException e) {
                role = null;
            }
            admin.createEmployee(u, req.form("user_id"), req.form("full_name"), req.form("password"), role);
            return "Employee created.";
        });
        return Response.redirect("/users");
    }

    private Response userAction(Request req) {
        User u = req.user();
        long id = req.paramLong("id");
        String action = req.param("action");
        switch (action) {
            case "unlock":
                attempt(req, () -> {
                    admin.unlock(u, id);
                    return "User unlocked.";
                });
                break;
            case "enable":
            case "disable":
                attempt(req, () -> {
                    admin.setActive(u, id, action.equals("enable"));
                    return "User " + action + "d.";
                });
                break;
            default:
                return null;
        }
        return Response.redirect(safeLocal(req.form("back"), "/users"));
    }

    private Response interest(Request req) {
        User u = req.user();
        attempt(req, () -> "Monthly interest credited to " + accounts.creditMonthlyInterest(u) + " savings accounts.");
        return Response.redirect("/");
    }

    private Response audit(Request req) {
        return Response.html(AdminViews.audit(ctx(req), admin.auditTrail(req.user())));
    }
}
