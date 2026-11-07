package com.passbook.web;

import com.passbook.model.AuditEntry;
import com.passbook.model.User;

import java.util.List;

public final class AdminViews {
    private AdminViews() {
    }

    public static String users(ViewContext c, List<User> employees, List<User> locked) {
        StringBuilder h = new StringBuilder("<header class=\"page-head\"><div><p class=\"eyebrow\">Administration</p><h1>Staff &amp; access</h1></div></header>")
                .append("<div class=\"two\"><form method=\"post\" action=\"/users\" class=\"slip\">").append(Html.csrf(c))
                .append("<h3>New employee</h3><label>User ID<input name=\"user_id\" required maxlength=\"40\"></label>")
                .append("<label>Full name<input name=\"full_name\" required maxlength=\"80\"></label>")
                .append("<label>Role<select name=\"role\"><option value=\"teller\">Teller</option><option value=\"manager\">Manager / Admin</option></select></label>")
                .append("<label>Initial password<input name=\"password\" type=\"password\" minlength=\"6\" required autocomplete=\"new-password\"></label>")
                .append("<button class=\"btn\" type=\"submit\">Create employee</button></form>")
                .append("<section class=\"book\"><h2>Locked users</h2><ul class=\"plain\">");
        for (User u : locked) {
            h.append("<li><span>").append(Html.esc(u.getUserId())).append(" <small>(").append(u.role().label()).append(")</small></span>")
                    .append("<form method=\"post\" action=\"/users/").append(u.getId()).append("/unlock\">").append(Html.csrf(c))
                    .append("<input type=\"hidden\" name=\"back\" value=\"/users\"><button class=\"mini\">Unlock</button></form></li>");
        }
        if (locked.isEmpty()) h.append("<li class=\"empty\">Nobody is locked out.</li>");
        h.append("</ul></section></div><section class=\"book\"><h2>Employees</h2><table class=\"ledger\"><thead><tr><th>User ID</th><th>Name</th><th>Role</th><th>State</th><th></th></tr></thead><tbody>");
        for (User u : employees) {
            h.append("<tr><td class=\"mono\">").append(Html.esc(u.getUserId())).append("</td><td>").append(Html.esc(u.getFullName()))
                    .append("</td><td>").append(u.role().label()).append("</td><td><span class=\"stamp ").append(u.isActive() ? "s-active" : "s-closed").append("\">")
                    .append(u.isActive() ? "Enabled" : "Disabled").append("</span></td><td>");
            if (!u.getUserId().equals(c.user().getUserId())) {
                h.append("<form method=\"post\" action=\"/users/").append(u.getId()).append(u.isActive() ? "/disable" : "/enable").append("\">").append(Html.csrf(c))
                        .append("<input type=\"hidden\" name=\"back\" value=\"/users\"><button class=\"mini\">").append(u.isActive() ? "Disable" : "Enable").append("</button></form>");
            }
            h.append("</td></tr>");
        }
        h.append("</tbody></table></section>");
        return Layout.page(c, "Staff &amp; access", h.toString());
    }

    public static String audit(ViewContext c, List<AuditEntry> entries) {
        StringBuilder h = new StringBuilder("<header class=\"page-head\"><div><p class=\"eyebrow\">Every critical action, with who and when</p><h1>Audit trail</h1></div></header>")
                .append("<section class=\"book\"><div class=\"tablewrap\"><table class=\"ledger\"><thead><tr><th>Time</th><th>User</th><th>Action</th><th>Detail</th></tr></thead><tbody>");
        for (AuditEntry e : entries) {
            h.append("<tr><td class=\"mono\">").append(Html.ts(e.createdAt())).append("</td><td>").append(Html.esc(e.actor()))
                    .append("</td><td class=\"mono\">").append(Html.esc(e.action())).append("</td><td>").append(Html.esc(e.detail())).append("</td></tr>");
        }
        h.append("</tbody></table></div></section>");
        return Layout.page(c, "Audit trail", h.toString());
    }
}
