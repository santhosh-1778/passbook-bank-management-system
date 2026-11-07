package com.passbook.web;

import com.passbook.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Small helpers every view uses. esc() must wrap every piece of user-supplied text. */
public final class Html {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Html() {
    }

    public static String esc(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o);
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String money(BigDecimal v) {
        return esc(Money.format(v));
    }

    public static String ts(LocalDateTime t) {
        return t == null ? "" : t.format(TS);
    }

    public static String csrf(ViewContext c) {
        return "<input type=\"hidden\" name=\"_csrf\" value=\"" + esc(c.csrf()) + "\">";
    }

    /** Escaped value of a submitted form field (to refill a form after an error). */
    public static String val(Map<String, String> form, String key) {
        return esc(form == null ? "" : form.getOrDefault(key, ""));
    }

    /** A rubber-stamp status mark. */
    public static String stamp(String label, boolean big) {
        return "<span class=\"stamp s-" + esc(label.toLowerCase()) + (big ? " big" : "") + "\">" + esc(label) + "</span>";
    }

    public static String stamp(String label) {
        return stamp(label, false);
    }
}
