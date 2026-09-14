"""Promote the smoke-test user to ADMIN inside the throwaway smoke database."""
import sqlite3
import sys

path = sys.argv[1]
con = sqlite3.connect(path)
con.execute("UPDATE users SET type = 2 WHERE username = 'smokeadmin'")
con.commit()
con.close()
print("promoted smokeadmin to ADMIN")
