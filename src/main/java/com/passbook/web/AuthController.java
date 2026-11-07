package com.passbook.web;

import com.passbook.exception.InvalidLoginException;
import com.passbook.model.User;
import com.passbook.service.AuthService;

public final class AuthController extends Controller {
    private final AuthService auth;
    private final SessionStore sessions;

    public AuthController(AuthService auth, SessionStore sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    @Override
    public void register(Router r) {
        r.publicRoute("GET", "/login", this::showLogin);
        r.publicRoute("POST", "/login", this::doLogin);
        r.publicRoute("POST", "/logout", this::logout);
    }

    private Response showLogin(Request req) {
        if (req.user() != null) return Response.redirect("/");
        return Response.html(AuthViews.login(ctx(req), req.query("next")));
    }

    private Response doLogin(Request req) {
        try {
            User u = auth.login(req.form("user_id"), req.form("password"));
            req.setSession(sessions.login(req.session(), u.getId()));
            return Response.redirect(safeLocal(req.query("next"), "/"));
        } catch (InvalidLoginException e) {
            flash(req, "err", e.getMessage());
            return Response.html(AuthViews.login(ctx(req), req.query("next")));
        }
    }

    private Response logout(Request req) {
        sessions.destroy(req.session());
        req.setSession(sessions.create());
        return Response.redirect("/login");
    }
}
