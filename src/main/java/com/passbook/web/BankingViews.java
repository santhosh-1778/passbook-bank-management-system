package com.passbook.web;

import com.passbook.model.Account;
import com.passbook.model.AccountStatus;
import com.passbook.model.CurrentAccount;
import com.passbook.model.CustomerProfile;
import com.passbook.model.Loan;
import com.passbook.model.SavingsAccount;
import com.passbook.model.Transaction;
import com.passbook.model.User;
import com.passbook.service.AccountDetails;
import com.passbook.service.AccountService;
import com.passbook.service.LoanView;
import com.passbook.service.Stats;

import java.util.List;
import java.util.Map;

/** Dashboards, account pages, transfer and account-opening forms. */
public final class BankingViews {
    private BankingViews() {
    }

    // ------------------------------------------------------------------ customer home
    public static String customerDashboard(ViewContext c, CustomerProfile me, List<Account> accounts, List<Loan> loans) {
        StringBuilder h = new StringBuilder();
        h.append("<header class=\"page-head\"><div><p class=\"eyebrow\">Welcome back</p><h1>").append(Html.esc(me.name()))
                .append("</h1></div><div class=\"actions\"><a class=\"btn\" href=\"/transfer\">Transfer funds</a>")
                .append("<a class=\"btn ghost\" href=\"/loans\">Apply for a loan</a></div></header>\n<section class=\"covers\">");
        for (Account a : accounts) {
            h.append("<a class=\"cover ").append(a.type()).append(a.getStatus() != AccountStatus.ACTIVE ? " dim" : "")
                    .append("\" href=\"/account/").append(Html.esc(a.getAccountNo())).append("\">")
                    .append("<span class=\"cover-top\"><span class=\"kind\">").append(a.type()).append(" account</span><span class=\"chip\"></span></span>")
                    .append("<span class=\"cover-no\">").append(Html.esc(a.getAccountNo())).append("</span>")
                    .append("<span class=\"cover-bal\"><small>Balance</small>").append(Html.money(a.getBalance())).append("</span>")
                    .append("<span class=\"cover-foot\">");
            if (a.getStatus() != AccountStatus.ACTIVE) h.append(Html.stamp(a.getStatus().label()));
            h.append(Html.esc(a.termsSummary()))  // polymorphic: each account type describes itself
                    .append("</span></a>");
        }
        if (accounts.isEmpty()) h.append("<p class=\"empty\">You have no accounts yet. Visit a teller to open one.</p>");
        h.append("</section>");
        if (!loans.isEmpty()) {
            h.append("<section class=\"book\"><h2>Your loans</h2><table class=\"ledger\"><thead><tr><th>#</th><th>Purpose</th>")
                    .append("<th class=\"num\">Principal</th><th class=\"num\">EMI</th><th class=\"num\">Outstanding</th><th>Status</th></tr></thead><tbody>");
            for (Loan l : loans) {
                h.append("<tr><td class=\"mono\">").append(l.getId()).append("</td><td>").append(Html.esc(l.getPurpose()))
                        .append("</td><td class=\"num\">").append(Html.money(l.getPrincipal()))
                        .append("</td><td class=\"num\">").append(Html.money(l.getEmi()))
                        .append("</td><td class=\"num\">").append(Html.money(l.getOutstanding()))
                        .append("</td><td>").append(Html.stamp(l.getStatus().label())).append("</td></tr>");
            }
            h.append("</tbody></table></section>");
        }
        return Layout.page(c, "Overview", h.toString());
    }

    // ------------------------------------------------------------------ staff home
    public static String staffDashboard(ViewContext c, Stats s, List<Transaction> recent, List<LoanView> pending,
                                        List<User> locked) {
        User u = c.user();
        boolean manager = u.can(com.passbook.model.Permission.APPROVE_LOAN);
        StringBuilder h = new StringBuilder();
        h.append("<header class=\"page-head\"><div><p class=\"eyebrow\">").append(Html.esc(u.role().label()))
                .append(" desk</p><h1>Today at the branch</h1></div><div class=\"actions\"><a class=\"btn\" href=\"/accounts/open\">Open account</a>");
        if (u.can(com.passbook.model.Permission.CREDIT_INTEREST)) {
            h.append("<form method=\"post\" action=\"/interest\" onsubmit=\"return confirm('Credit one month of interest to every active savings account?')\">")
                    .append(Html.csrf(c)).append("<button class=\"btn ghost\" type=\"submit\">Credit monthly interest</button></form>");
        }
        h.append("</div></header>\n<section class=\"tally\">")
                .append("<div><small>Customers</small><b>").append(s.customers()).append("</b></div>")
                .append("<div><small>Open accounts</small><b>").append(s.openAccounts()).append("</b></div>")
                .append("<div><small>Deposits held</small><b>").append(Html.money(s.deposits())).append("</b></div>")
                .append("<div><small>Loan book</small><b>").append(Html.money(s.loanBook())).append("</b></div>");
        if (manager) {
            h.append("<div class=\"hot\"><small>Loans awaiting you</small><b>").append(s.pendingLoans()).append("</b></div>");
        }
        h.append("</section>");
        if (manager && (!pending.isEmpty() || !locked.isEmpty())) {
            h.append("<div class=\"two\">");
            if (!pending.isEmpty()) {
                h.append("<section class=\"book\"><h2>Pending loans</h2><ul class=\"plain\">");
                for (LoanView v : pending.subList(0, Math.min(5, pending.size()))) {
                    Loan l = v.loan();
                    h.append("<li><span><b>#").append(l.getId()).append("</b> ").append(Html.esc(v.ownerName())).append(" · ")
                            .append(Html.money(l.getPrincipal())).append(" over ").append(l.getTenureMonths()).append(" mo</span></li>");
                }
                h.append("</ul><a class=\"linkbtn\" href=\"/loans/review\">Open the loan desk →</a></section>");
            }
            if (!locked.isEmpty()) {
                h.append("<section class=\"book\"><h2>Locked users</h2><ul class=\"plain\">");
                for (User l : locked) {
                    h.append("<li><span>").append(Html.esc(l.getUserId())).append(" <small>(").append(l.role().label())
                            .append(")</small></span><form method=\"post\" action=\"/users/").append(l.getId()).append("/unlock\">")
                            .append(Html.csrf(c)).append("<input type=\"hidden\" name=\"back\" value=\"/\"><button class=\"mini\">Unlock</button></form></li>");
                }
                h.append("</ul></section>");
            }
            h.append("</div>");
        }
        h.append("<section class=\"book\"><h2>Latest entries across the branch</h2><table class=\"ledger\"><thead><tr>")
                .append("<th>Time</th><th>Account</th><th>Type</th><th class=\"num\">Amount</th></tr></thead><tbody>");
        for (Transaction t : recent) {
            h.append("<tr><td class=\"mono\">").append(Html.ts(t.createdAt())).append("</td><td><a class=\"mono\" href=\"/account/")
                    .append(Html.esc(t.accountNo())).append("\">").append(Html.esc(t.accountNo())).append("</a></td><td>")
                    .append(t.type().label()).append("</td><td class=\"num ").append(t.type().isCredit() ? "cr" : "dr").append("\">")
                    .append(t.type().isCredit() ? "+" : "−").append(Html.money(t.amount())).append("</td></tr>");
        }
        if (recent.isEmpty()) h.append("<tr><td colspan=\"4\" class=\"empty\">No transactions yet.</td></tr>");
        h.append("</tbody></table></section>");
        return Layout.page(c, "Overview", h.toString());
    }

    // ------------------------------------------------------------------ one account
    private static String ledgerRows(List<Transaction> list) {
        StringBuilder h = new StringBuilder();
        for (Transaction t : list) {
            h.append("<tr><td class=\"mono\">").append(t.id()).append("</td><td class=\"mono\">").append(Html.ts(t.createdAt()))
                    .append("</td><td>").append(t.type().label());
            if (t.note() != null && !t.note().isEmpty()) {
                h.append("<small class=\"note-inline\"> · ").append(Html.esc(t.note())).append("</small>");
            }
            h.append("</td><td class=\"num cr\">").append(t.type().isCredit() ? Html.money(t.amount()) : "")
                    .append("</td><td class=\"num dr\">").append(t.type().isCredit() ? "" : Html.money(t.amount()))
                    .append("</td><td class=\"num\">").append(Html.money(t.balanceAfter())).append("</td></tr>");
        }
        if (list.isEmpty()) h.append("<tr><td colspan=\"6\" class=\"empty\">No entries.</td></tr>");
        return h.toString();
    }

    public static String account(ViewContext c, AccountDetails d, List<Transaction> mini, List<Transaction> statement,
                                 String from, String to) {
        Account a = d.account();
        String no = Html.esc(a.getAccountNo());
        User u = c.user();
        StringBuilder h = new StringBuilder();
        h.append("<header class=\"page-head\"><div><p class=\"eyebrow\">").append(a.type()).append(" account · ")
                .append(Html.esc(d.ownerName())).append("</p><h1 class=\"mono-h\">").append(no).append("</h1></div>")
                .append(Html.stamp(a.getStatus().label(), true)).append("</header>\n");
        h.append("<section class=\"tally\"><div class=\"hot\"><small>Current balance</small><b>").append(Html.money(a.getBalance())).append("</b></div>");
        if (a instanceof SavingsAccount) {
            SavingsAccount s = (SavingsAccount) a;
            h.append("<div><small>Minimum balance</small><b>").append(Html.money(s.getMinBalance())).append("</b></div>")
                    .append("<div><small>Interest</small><b>").append(s.annualRatePercent()).append("% p.a.</b></div>");
        } else if (a instanceof CurrentAccount) {
            h.append("<div><small>Overdraft limit</small><b>").append(Html.money(((CurrentAccount) a).getOverdraftLimit())).append("</b></div>");
        }
        h.append("<div><small>Opened</small><b>").append(a.getOpenDate()).append("</b></div></section>\n");

        if (a.getStatus() == AccountStatus.ACTIVE) {
            h.append("<div class=\"two\">")
                    .append(moneyForm(c, no, "deposit", "Deposit", "Record deposit", "btn"))
                    .append(moneyForm(c, no, "withdraw", "Withdraw", "Record withdrawal", "btn ghost"))
                    .append("</div>");
        }

        boolean ranged = statement != null;
        h.append("<section class=\"book\"><h2>").append(ranged ? "Statement · " + Html.esc(from) + " to " + Html.esc(to)
                        : "Mini-statement · last 10 entries").append("</h2><div class=\"tablewrap\"><table class=\"ledger\"><thead><tr>")
                .append("<th>#</th><th>Date &amp; time</th><th>Particulars</th><th class=\"num\">Credit</th><th class=\"num\">Debit</th><th class=\"num\">Balance</th></tr></thead><tbody>")
                .append(ledgerRows(ranged ? statement : mini)).append("</tbody></table></div>")
                .append("<form method=\"get\" class=\"daterange\"><span>Statement for a date range</span>")
                .append("<input type=\"date\" name=\"from\" value=\"").append(Html.esc(from)).append("\" max=\"").append(c.today()).append("\" required>")
                .append("<input type=\"date\" name=\"to\" value=\"").append(to == null || to.isEmpty() ? c.today().toString() : Html.esc(to))
                .append("\" max=\"").append(c.today()).append("\" required><button class=\"mini\" type=\"submit\">Show</button>");
        if (ranged) h.append("<a class=\"linkbtn\" href=\"/account/").append(no).append("\">Back to mini-statement</a>");
        h.append("</form></section>");

        boolean canClose = u.can(com.passbook.model.Permission.CLOSE_ACCOUNT);
        if (canClose && a.getStatus() != AccountStatus.CLOSED) {
            h.append("<section class=\"book danger-zone\"><h2>Account controls</h2><div class=\"actions\">")
                    .append("<form method=\"post\" action=\"/account/").append(no).append("/close\" onsubmit=\"return confirm('Close this account? This cannot be undone.')\">")
                    .append(Html.csrf(c)).append("<button class=\"btn danger\">Close account</button></form>");
            if (u.can(com.passbook.model.Permission.FREEZE_ACCOUNT)) {
                boolean active = a.getStatus() == AccountStatus.ACTIVE;
                h.append("<form method=\"post\" action=\"/account/").append(no).append(active ? "/freeze" : "/unfreeze").append("\">")
                        .append(Html.csrf(c)).append("<button class=\"btn ghost\">")
                        .append(active ? "Freeze for suspicious activity" : "Clear freeze").append("</button></form>");
            }
            h.append("</div><p class=\"fine\">Closing needs a zero balance and no active or pending loan.</p></section>");
        }
        return Layout.page(c, "Account " + no, h.toString());
    }

    private static String moneyForm(ViewContext c, String no, String action, String title, String button, String css) {
        return "<form method=\"post\" action=\"/account/" + no + "/" + action + "\" class=\"slip inline\">" + Html.csrf(c)
                + "<h3>" + title + "</h3><label>Amount<input name=\"amount\" inputmode=\"decimal\" required placeholder=\"0.00\"></label>"
                + "<button class=\"" + css + "\" type=\"submit\">" + button + "</button></form>";
    }

    // ------------------------------------------------------------------ transfer
    public static String transfer(ViewContext c, List<Account> mine) {
        StringBuilder h = new StringBuilder("<header class=\"page-head\"><div><p class=\"eyebrow\">Internal transfer</p><h1>Move money between accounts</h1></div></header>");
        if (mine.isEmpty()) {
            h.append("<p class=\"empty\">You need an active account to transfer funds.</p>");
        } else {
            h.append("<form method=\"post\" class=\"slip wide\">").append(Html.csrf(c)).append("<label>From<select name=\"from_no\" required>");
            for (Account a : mine) {
                h.append("<option value=\"").append(Html.esc(a.getAccountNo())).append("\">").append(Html.esc(a.getAccountNo()))
                        .append(" · ").append(a.type()).append(" · ").append(Html.money(a.getBalance())).append("</option>");
            }
            h.append("</select></label><label>To account number<input name=\"to_no\" required inputmode=\"numeric\" placeholder=\"10-digit account number\"></label>")
                    .append("<label>Amount<input name=\"amount\" required inputmode=\"decimal\" placeholder=\"0.00\"></label>")
                    .append("<button class=\"btn\" type=\"submit\">Confirm transfer</button>")
                    .append("<p class=\"fine\">The debit and credit happen together. If anything fails, neither balance changes.</p></form>");
        }
        return Layout.page(c, "Transfer funds", h.toString());
    }

    // ------------------------------------------------------------------ open account
    public static String openAccount(ViewContext c, List<AccountService.CustomerRow> customers, Map<String, String> f) {
        StringBuilder h = new StringBuilder("<header class=\"page-head\"><div><p class=\"eyebrow\">Teller counter</p><h1>Open a new account</h1></div></header>");
        h.append("<form method=\"post\" class=\"slip wide\" id=\"openForm\">").append(Html.csrf(c))
                .append("<fieldset><legend>Customer</legend><label>Existing customer<select name=\"customer_id\" id=\"existing\"><option value=\"\">— new customer —</option>");
        String chosen = f.getOrDefault("customer_id", "");
        for (AccountService.CustomerRow r : customers) {
            CustomerProfile p = r.profile();
            h.append("<option value=\"").append(p.id()).append("\"").append(String.valueOf(p.id()).equals(chosen) ? " selected" : "")
                    .append(">").append(Html.esc(p.name())).append(" · #").append(p.id()).append(" · ").append(Html.esc(p.phone())).append("</option>");
        }
        h.append("</select></label><div id=\"newFields\">")
                .append("<label>Full name<input name=\"name\" value=\"").append(Html.val(f, "name")).append("\"></label>")
                .append("<label>Address<input name=\"address\" value=\"").append(Html.val(f, "address")).append("\"></label>")
                .append("<label>Phone<input name=\"phone\" inputmode=\"tel\" value=\"").append(Html.val(f, "phone")).append("\"></label>")
                .append("<label>ID proof (type and number)<input name=\"id_proof\" value=\"").append(Html.val(f, "id_proof")).append("\"></label></div></fieldset>")
                .append("<fieldset><legend>Account</legend><label>Type<select name=\"type\" id=\"atype\">")
                .append("<option value=\"savings\"").append("savings".equals(f.get("type")) ? " selected" : "").append(">Savings (minimum balance 1,000 · 4% p.a.)</option>")
                .append("<option value=\"current\"").append("current".equals(f.get("type")) ? " selected" : "").append(">Current (overdraft allowed)</option></select></label>")
                .append("<label>Opening deposit<input name=\"opening\" inputmode=\"decimal\" required placeholder=\"0.00\" value=\"").append(Html.val(f, "opening")).append("\"></label>")
                .append("<label id=\"odRow\">Overdraft limit<input name=\"overdraft\" inputmode=\"decimal\" value=\"")
                .append(f.containsKey("overdraft") ? Html.val(f, "overdraft") : "0").append("\"></label></fieldset>")
                .append("<button class=\"btn\" type=\"submit\">Open account</button></form>")
                .append("""
                        <script>
                        (function(){
                          var ex=document.getElementById('existing'), nf=document.getElementById('newFields'),
                              at=document.getElementById('atype'), od=document.getElementById('odRow');
                          function sync(){
                            var isNew=!ex.value; nf.style.display=isNew?'':'none';
                            nf.querySelectorAll('input').forEach(function(i){i.required=isNew;});
                            od.style.display=at.value==='current'?'':'none';
                          }
                          ex.addEventListener('change',sync); at.addEventListener('change',sync); sync();
                        })();
                        </script>
                        """);
        return Layout.page(c, "Open account", h.toString());
    }

    // ------------------------------------------------------------------ customers
    public static String customers(ViewContext c, List<AccountService.CustomerRow> rows, String q) {
        StringBuilder h = new StringBuilder("<header class=\"page-head\"><div><p class=\"eyebrow\">Register</p><h1>Customers</h1></div>")
                .append("<form method=\"get\" class=\"search\"><input name=\"q\" value=\"").append(Html.esc(q))
                .append("\" placeholder=\"Search name, phone or ID\"><button class=\"mini\">Search</button></form></header>")
                .append("<section class=\"book\"><div class=\"tablewrap\"><table class=\"ledger\"><thead><tr><th>ID</th><th>Name</th><th>Phone</th><th>Address</th><th class=\"num\">Open accounts</th><th></th></tr></thead><tbody>");
        for (AccountService.CustomerRow r : rows) {
            CustomerProfile p = r.profile();
            h.append("<tr><td class=\"mono\">").append(p.id()).append("</td><td>").append(Html.esc(p.name())).append("</td><td class=\"mono\">")
                    .append(Html.esc(p.phone())).append("</td><td>").append(Html.esc(p.address())).append("</td><td class=\"num\">")
                    .append(r.openAccounts()).append("</td><td><a class=\"linkbtn\" href=\"/customers/").append(p.id()).append("\">Edit profile</a></td></tr>");
        }
        if (rows.isEmpty()) h.append("<tr><td colspan=\"6\" class=\"empty\">No customers match.</td></tr>");
        h.append("</tbody></table></div></section>");
        return Layout.page(c, "Customers", h.toString());
    }

    public static String customerEdit(ViewContext c, long id, Map<String, String> f, List<Account> accounts) {
        StringBuilder h = new StringBuilder("<header class=\"page-head\"><div><p class=\"eyebrow\">Customer #").append(id)
                .append("</p><h1>Update profile</h1></div></header><form method=\"post\" class=\"slip wide\">").append(Html.csrf(c))
                .append("<label>Full name<input name=\"name\" required value=\"").append(Html.val(f, "name")).append("\"></label>")
                .append("<label>Address<input name=\"address\" required value=\"").append(Html.val(f, "address")).append("\"></label>")
                .append("<label>Phone<input name=\"phone\" required value=\"").append(Html.val(f, "phone")).append("\"></label>")
                .append("<label>ID proof<input name=\"id_proof\" required value=\"").append(Html.val(f, "id_proof")).append("\"></label>")
                .append("<button class=\"btn\" type=\"submit\">Save changes</button></form>")
                .append("<section class=\"book\"><h2>Accounts</h2><ul class=\"plain\">");
        for (Account a : accounts) {
            h.append("<li><a class=\"mono\" href=\"/account/").append(Html.esc(a.getAccountNo())).append("\">").append(Html.esc(a.getAccountNo()))
                    .append("</a><span>").append(a.type()).append(" · ").append(Html.money(a.getBalance())).append("</span>")
                    .append(Html.stamp(a.getStatus().label())).append("</li>");
        }
        if (accounts.isEmpty()) h.append("<li class=\"empty\">No accounts.</li>");
        h.append("</ul></section>");
        return Layout.page(c, "Customer #" + id, h.toString());
    }
}
