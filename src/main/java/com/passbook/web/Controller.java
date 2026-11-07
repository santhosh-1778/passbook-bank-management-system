package com.passbook.web;

import com.passbook.exception.AccessDeniedException;
import com.passbook.exception.BankException;

import java.time.LocalDate;
import java.util.function.Supplier;

/** Base class for controllers: shared helpers, one register() method per subclass (INHERITANCE + ABSTRACTION). */
public abstract class Controller {
    /** Adds this controller's routes to the router. */
    public abstract void register(Router router);

    protected ViewContext ctx(Request req) {
        return new ViewContext(req.user(), req.session().csrf(), req.path(), req.session().drainFlashes(), LocalDate.now());
    }

    protected void flash(Request req, String kind, String message) {
        req.session().flash(kind, message);
    }

    /**
     * Runs a user action. A BankException becomes a red note on the next page instead of an error page.
     * The action returns the success message (or null for none). Returns true on success.
     */
    protected boolean attempt(Request req, Supplier<String> action) {
        try {
            String ok = action.get();
            if (ok != null) flash(req, "ok", ok);
            return true;
        } catch (AccessDeniedException e) {
            throw e;  // not a form mistake: show the 403 page
        } catch (BankException e) {
            flash(req, "err", e.getMessage());
            return false;
        }
    }

    protected static int intField(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new BankException("Check the numbers you entered.");
        }
    }

    /** Only same-site relative paths are allowed as redirect targets. */
    protected static String safeLocal(String target, String fallback) {
        return target != null && target.startsWith("/") && !target.startsWith("//") ? target : fallback;
    }
}
