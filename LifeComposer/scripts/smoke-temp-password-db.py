"""Small helpers for scripts/smoke-temp-password.sh (throwaway smoke database only).

Usage:
    python3 scripts/smoke-temp-password-db.py promote <db>
    python3 scripts/smoke-temp-password-db.py expire  <db>
"""
import sqlite3
import sys
import time

action, path = sys.argv[1], sys.argv[2]
con = sqlite3.connect(path)

if action == "promote":
    con.execute("UPDATE users SET type = 2 WHERE username = 'smokeadmin'")
    print("  promoted smokeadmin to ADMIN (copy only)")
elif action == "expire":
    con.execute(
        "UPDATE users SET temp_password_expires_at = ? WHERE username = 'smoketarget'",
        (int((time.time() - 60) * 1000),),
    )
    print("  moved temp_password_expires_at into the past")
else:
    sys.exit("unknown action: " + action)

con.commit()
con.close()
