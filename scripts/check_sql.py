"""Checks every SQL statement used by the Java SQLite repositories against a real SQLite engine.
Usage: python3 scripts/check_sql.py   (run from the project folder)
It prepares each statement (syntax + table/column names) and runs the INSERTs with realistic values
so the CHECK constraints are exercised too."""
import glob, re, sqlite3, sys

LIT = r'"(?:[^"\\]|\\.)*"'
repo_dir = "src/main/java/com/passbook/repo/sqlite/"

schema_src = open(repo_dir + "Schema.java").read()
ddl = re.findall(r'"""\s*(.*?)"""|("(?:[^"\\]|\\.)*")', schema_src, re.S)
statements = []
for block, lit in ddl:
    s = block if block else (lit[1:-1] if lit else "")
    if s.strip().upper().startswith("CREATE"):
        statements.append(s)

db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys = ON")
for s in statements:
    db.execute(s)
print(f"schema: {len(statements)} statements OK")

total = 0
bad = 0
for path in sorted(glob.glob(repo_dir + "Sqlite*Repository.java")):
    src = open(path).read()
    consts = dict(re.findall(r'(?:private static final String|static final String)\s+(\w+)\s*=\s*\n?\s*(' + LIT + r')\s*;', src))
    consts = {k: v[1:-1] for k, v in consts.items()}
    for m in re.finditer(r'db\.(?:query|queryOne|update|insert)\(\s*((?:\w+|' + LIT + r')(?:\s*\+\s*(?:\w+|' + LIT + r'))*)', src):
        parts = re.findall(LIT + r'|\w+', m.group(1))
        sql = ""
        for p in parts:
            sql += p[1:-1] if p.startswith('"') else consts[p]
        n = sql.count("?")
        total += 1
        try:
            db.execute("EXPLAIN " + sql, [None] * n).fetchall()
        except sqlite3.Error as e:
            bad += 1
            print("FAIL", path.split("/")[-1], "->", sql, "\n    ", e)

# realistic inserts, to exercise the CHECK / FOREIGN KEY constraints
db.execute("INSERT INTO customers(name,address,phone,id_proof,created_at) VALUES('A','B','999','X','2026-11-05 10:00:00')")
db.execute("INSERT INTO users(user_id,full_name,password_hash,role,customer_id,failed_attempts,locked,active) VALUES('alice','A','h','customer',1,0,0,1)")
db.execute("INSERT INTO users(user_id,full_name,password_hash,role,customer_id,failed_attempts,locked,active) VALUES('admin','M','h','manager',NULL,0,0,1)")
db.execute("INSERT INTO users(user_id,full_name,password_hash,role,customer_id,failed_attempts,locked,active) VALUES('t1','T','h','teller',NULL,0,0,1)")
db.execute("INSERT INTO accounts(seq,account_no,customer_id,type,balance,min_balance,interest_rate,overdraft_limit,status,open_date) VALUES(1,'5000100001',1,'savings',25000.0,1000.0,4.0,NULL,'Active','2026-11-05')")
db.execute("INSERT INTO accounts(seq,account_no,customer_id,type,balance,min_balance,interest_rate,overdraft_limit,status,open_date) VALUES(2,'5000100002',1,'current',8000.0,NULL,NULL,5000.0,'Frozen','2026-11-05')")
db.execute("INSERT INTO transactions(account_no,type,amount,balance_after,note,created_at) VALUES('5000100001','TRANSFER_OUT',10.5,24989.5,'To x','2026-11-05 10:00:00')")
db.execute("INSERT INTO loans(customer_id,account_no,principal,rate,tenure_months,purpose,emi,outstanding,status,applied_at) VALUES(1,'5000100001',12000.0,10.0,12,'Bike',1054.99,0.0,'Applied','2026-11-05 10:00:00')")
db.execute("INSERT INTO audit_log(actor,action,detail,created_at) VALUES('admin','LOGIN','','2026-11-05 10:00:00')")
print("sample rows inserted OK (CHECK and FOREIGN KEY constraints accepted the values Java sends)")

# every enum label Java writes must satisfy the CHECKs
for role in ("customer", "teller", "manager"):
    db.execute("INSERT INTO users(user_id,full_name,password_hash,role) VALUES(?,?,?,?)", ("u_" + role, "n", "h", role))
for st in ("Active", "Frozen", "Closed"):
    db.execute("UPDATE accounts SET status=? WHERE account_no='5000100001'", (st,))
for st in ("Applied", "Rejected", "Disbursed", "Closed"):
    db.execute("UPDATE loans SET status=? WHERE id=1", (st,))
print("all role / status labels accepted")

print(f"{total} repository statements prepared, {bad} failed")
sys.exit(1 if bad else 0)
