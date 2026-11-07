package com.passbook.web;

import com.passbook.model.Account;
import com.passbook.model.Loan;
import com.passbook.model.LoanStatus;
import com.passbook.service.LoanView;

import java.util.List;
import java.util.Map;

public final class LoanViews {
    private LoanViews() {
    }

    public static String loans(ViewContext c, List<Account> accounts, List<Loan> loans, double rate, Map<String, String> f) {
        StringBuilder h = new StringBuilder("<header class=\"page-head\"><div><p class=\"eyebrow\">Borrowing</p><h1>Loans</h1></div></header>")
                .append("<div class=\"two\"><form method=\"post\" class=\"slip\" id=\"loanForm\">").append(Html.csrf(c))
                .append("<h3>Apply for a loan</h3><label>Credit the loan to<select name=\"account_no\" required>");
        for (Account a : accounts) {
            h.append("<option value=\"").append(Html.esc(a.getAccountNo())).append("\">").append(Html.esc(a.getAccountNo())).append(" · ").append(a.type()).append("</option>");
        }
        h.append("</select></label><label>Amount<input name=\"amount\" id=\"amt\" inputmode=\"decimal\" required value=\"").append(Html.val(f, "amount"))
                .append("\" placeholder=\"1,000 to 5,000,000\"></label><label>Tenure (months)<input name=\"tenure\" id=\"ten\" inputmode=\"numeric\" required value=\"")
                .append(Html.val(f, "tenure")).append("\" placeholder=\"3 to 360\"></label><label>Purpose<input name=\"purpose\" required maxlength=\"120\" value=\"")
                .append(Html.val(f, "purpose")).append("\"></label><div class=\"emi-box\">Estimated EMI at ").append(rate)
                .append("% p.a. <b id=\"emi\">—</b></div><button class=\"btn\" type=\"submit\">Submit application</button></form>")
                .append("<section class=\"book\"><h2>How it works</h2><ol class=\"steps\"><li>You apply; the EMI is worked out for you.</li>")
                .append("<li>A manager approves or rejects it.</li><li>On approval the money lands in your chosen account.</li>")
                .append("<li>Repay any amount from your accounts until it is cleared.</li></ol></section></div>")
                .append("<section class=\"book\"><h2>Your applications</h2><div class=\"tablewrap\"><table class=\"ledger\"><thead><tr><th>#</th><th>Purpose</th>")
                .append("<th class=\"num\">Principal</th><th class=\"num\">EMI</th><th class=\"num\">Outstanding</th><th>Status</th><th>Repay</th></tr></thead><tbody>");
        for (Loan l : loans) {
            h.append("<tr><td class=\"mono\">").append(l.getId()).append("</td><td>").append(Html.esc(l.getPurpose()))
                    .append("<small class=\"note-inline\"> · ").append(l.getTenureMonths()).append(" mo</small></td><td class=\"num\">")
                    .append(Html.money(l.getPrincipal())).append("</td><td class=\"num\">").append(Html.money(l.getEmi()))
                    .append("</td><td class=\"num\">").append(Html.money(l.getOutstanding())).append("</td><td>")
                    .append(Html.stamp(l.getStatus().label())).append("</td><td>");
            if (l.getStatus() == LoanStatus.DISBURSED) {
                h.append("<form method=\"post\" action=\"/loans/").append(l.getId()).append("/repay\" class=\"payrow\">").append(Html.csrf(c)).append("<select name=\"account_no\">");
                for (Account a : accounts) {
                    String no = a.getAccountNo();
                    h.append("<option value=\"").append(Html.esc(no)).append("\">").append(Html.esc(no.substring(no.length() - 4)))
                            .append(" · ").append(Html.money(a.getBalance())).append("</option>");
                }
                h.append("</select><input name=\"amount\" value=\"").append(l.getEmi().toPlainString())
                        .append("\" inputmode=\"decimal\" aria-label=\"Repayment amount\"><button class=\"mini\">Pay</button></form>");
            }
            h.append("</td></tr>");
        }
        if (loans.isEmpty()) h.append("<tr><td colspan=\"7\" class=\"empty\">No applications yet.</td></tr>");
        h.append("</tbody></table></div></section>")
                .append("<script>(function(){var a=document.getElementById('amt'),t=document.getElementById('ten'),out=document.getElementById('emi'),rate=").append(rate)
                .append(";function calc(){var P=parseFloat((a.value||'').replace(/,/g,'')),n=parseInt(t.value,10);if(!(P>0)||!(n>0)){out.textContent='—';return;}")
                .append("var r=rate/12/100,f=Math.pow(1+r,n),emi=P*r*f/(f-1);out.textContent=emi.toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2});}")
                .append("a.addEventListener('input',calc);t.addEventListener('input',calc);calc();})();</script>");
        return Layout.page(c, "Loans", h.toString());
    }

    public static String review(ViewContext c, List<LoanView> loans) {
        StringBuilder h = new StringBuilder("<header class=\"page-head\"><div><p class=\"eyebrow\">Manager</p><h1>Loan desk</h1></div></header>")
                .append("<section class=\"book\"><div class=\"tablewrap\"><table class=\"ledger\"><thead><tr><th>#</th><th>Customer</th><th>Purpose</th>")
                .append("<th class=\"num\">Principal</th><th class=\"num\">Tenure</th><th class=\"num\">EMI</th><th>Status</th><th>Decision</th></tr></thead><tbody>");
        for (LoanView v : loans) {
            Loan l = v.loan();
            h.append("<tr><td class=\"mono\">").append(l.getId()).append("</td><td>").append(Html.esc(v.ownerName())).append("</td><td>")
                    .append(Html.esc(l.getPurpose())).append("</td><td class=\"num\">").append(Html.money(l.getPrincipal()))
                    .append("</td><td class=\"num\">").append(l.getTenureMonths()).append(" mo</td><td class=\"num\">").append(Html.money(l.getEmi()))
                    .append("</td><td>").append(Html.stamp(l.getStatus().label())).append("</td><td>");
            if (l.getStatus() == LoanStatus.APPLIED) {
                h.append("<form method=\"post\" action=\"/loans/").append(l.getId()).append("/decide\" class=\"payrow\">").append(Html.csrf(c))
                        .append("<button class=\"mini ok\" name=\"decision\" value=\"approve\">Approve</button>")
                        .append("<button class=\"mini no\" name=\"decision\" value=\"reject\">Reject</button></form>");
            } else {
                h.append("<small class=\"note-inline\">").append(Html.esc(l.getDecidedBy())).append("</small>");
            }
            h.append("</td></tr>");
        }
        if (loans.isEmpty()) h.append("<tr><td colspan=\"8\" class=\"empty\">No loan applications.</td></tr>");
        h.append("</tbody></table></div></section>");
        return Layout.page(c, "Loan desk", h.toString());
    }
}
