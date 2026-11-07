package com.passbook.web;

import com.passbook.model.Permission;
import com.passbook.model.User;

import java.util.ArrayList;
import java.util.List;

/** The page frame: green spine with navigation, flash notes and the content area. */
public final class Layout {
    private Layout() {
    }

    private record Nav(String label, String href, String... activePrefixes) {
        boolean activeFor(String path) {
            if (href.equals("/")) return path.equals("/");
            if (path.equals(href)) return true;
            for (String p : activePrefixes) if (path.startsWith(p)) return true;
            return false;
        }
    }

    private static List<Nav> navFor(User u) {
        List<Nav> nav = new ArrayList<>();
        nav.add(new Nav("Overview", "/"));
        if (u.can(Permission.SELF_SERVICE)) {
            nav.add(new Nav("Transfer funds", "/transfer"));
            nav.add(new Nav("Loans", "/loans"));
        }
        if (u.can(Permission.OPEN_ACCOUNT)) nav.add(new Nav("Open account", "/accounts/open"));
        if (u.can(Permission.MANAGE_CUSTOMERS)) nav.add(new Nav("Customers", "/customers", "/customers/"));
        if (u.can(Permission.APPROVE_LOAN)) nav.add(new Nav("Loan desk", "/loans/review"));
        if (u.can(Permission.MANAGE_STAFF)) nav.add(new Nav("Staff &amp; access", "/users"));
        if (u.can(Permission.VIEW_AUDIT)) nav.add(new Nav("Audit trail", "/audit"));
        return nav;
    }

    public static String page(ViewContext c, String title, String body) {
        return page(c, title, "", body);
    }

    public static String page(ViewContext c, String title, String bodyClass, String body) {
        StringBuilder h = new StringBuilder(8192);
        h.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>").append(title).append(" · Passbook</title>\n")
                .append("<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n")
                .append("<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>\n")
                .append("<link href=\"https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,500;9..144,700&family=IBM+Plex+Mono:wght@400;500&family=Public+Sans:wght@400;500;600&display=swap\" rel=\"stylesheet\">\n")
                .append("<link rel=\"stylesheet\" href=\"/static/style.css\">\n</head>\n")
                .append("<body class=\"").append(bodyClass).append("\">\n");
        if (c.user() != null) {
            User u = c.user();
            h.append("<div class=\"shell\">\n<aside class=\"spine\">\n")
                    .append("<a class=\"brand\" href=\"/\"><span class=\"brand-mark\">P</span>")
                    .append("<span class=\"brand-name\">Passbook<small>Bank Management</small></span></a>\n<nav>\n");
            for (Nav n : navFor(u)) {
                h.append("<a href=\"").append(n.href()).append("\"").append(n.activeFor(c.path()) ? " class=\"on\"" : "")
                        .append(">").append(n.label()).append("</a>\n");
            }
            h.append("</nav>\n<div class=\"who\"><span class=\"who-name\">").append(Html.esc(u.getFullName()))
                    .append("</span><span class=\"stamp s-").append(u.role().label()).append("\">")
                    .append(u.role().label()).append("</span>\n")
                    .append("<form method=\"post\" action=\"/logout\">").append(Html.csrf(c))
                    .append("<button class=\"linkbtn\" type=\"submit\">Sign out</button></form></div>\n</aside>\n")
                    .append("<main class=\"sheet\">\n").append(flashes(c)).append(body).append("\n</main>\n</div>\n");
        } else {
            h.append(flashes(c)).append(body);
        }
        return h.append("\n</body>\n</html>\n").toString();
    }

    private static String flashes(ViewContext c) {
        if (c.flashes().isEmpty()) return "";
        StringBuilder h = new StringBuilder("<div class=\"notes\" role=\"status\">");
        for (Flash f : c.flashes()) {
            h.append("<div class=\"note n-").append(Html.esc(f.kind())).append("\">")
                    .append(Html.esc(f.message())).append("</div>");
        }
        return h.append("</div>").toString();
    }
}
