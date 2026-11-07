package com.passbook.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class AuthViews {
    private AuthViews() {
    }

    public static String login(ViewContext c, String next) {
        String action = "/login" + (next == null || next.isEmpty() ? ""
                : "?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
        String body = """
                <div class="gate-wrap">
                  <section class="gate-cover">
                    <div class="emboss">
                      <span class="brand-mark big">P</span>
                      <h1>Passbook</h1>
                      <p>A single-branch bank, kept the old way: one ledger, every entry accounted for.</p>
                    </div>
                    <ul class="demo">
                      <li><b>Customer</b> alice / alice123</li>
                      <li><b>Teller</b> teller1 / teller123</li>
                      <li><b>Manager</b> admin / admin123</li>
                    </ul>
                  </section>
                  <section class="gate-form">
                    <form method="post" action="%s" class="slip">
                      %s
                      <h2>Sign in</h2>
                      <label>User ID<input name="user_id" autocomplete="username" required autofocus></label>
                      <label>Password<input name="password" type="password" autocomplete="current-password" required></label>
                      <button class="btn" type="submit">Enter the ledger</button>
                      <p class="fine">Three wrong passwords lock the user. Sessions end after 10 idle minutes.</p>
                    </form>
                  </section>
                </div>
                """.formatted(Html.esc(action), Html.csrf(c));
        return Layout.page(c, "Sign in", "gate", body);
    }

    public static String error(ViewContext c, int code, String title, String message) {
        String body = "<div class=\"errpage\"><span class=\"stamp s-closed big\">" + code + "</span><h1>"
                + Html.esc(title) + "</h1><p>" + Html.esc(message) + "</p><a class=\"btn\" href=\"/\">Back to the ledger</a></div>";
        return Layout.page(c, String.valueOf(code), body);
    }
}
