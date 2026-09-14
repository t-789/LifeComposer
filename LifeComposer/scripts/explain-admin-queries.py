"""Check EXPLAIN QUERY PLAN for the admin console queries against a data copy."""
import sqlite3
import sys

DB = sys.argv[1] if len(sys.argv) > 1 else "target/smoke/smoke.db"
con = sqlite3.connect(DB)

TS_MSG = "CASE WHEN typeof(m.create_time) IN ('integer','real') THEN strftime('%Y-%m-%dT%H:%M:%SZ', m.create_time/1000, 'unixepoch') ELSE m.create_time END"
DAY_MSG = "CASE WHEN typeof(m.create_time) IN ('integer','real') THEN date(m.create_time/1000,'unixepoch') ELSE date(m.create_time) END"
DAY_PLAN = "CASE WHEN typeof(h.created_at) IN ('integer','real') THEN date(h.created_at/1000,'unixepoch') ELSE date(h.created_at) END"

QUERIES = {
    "chat-messages by user (list)": (
        "SELECT m.id, m.role, " + TS_MSG + " AS createTime FROM chat_messages m "
        "LEFT JOIN users u ON u.id = m.user_id WHERE 1=1 AND m.user_id = 3 ORDER BY m.id DESC LIMIT 50 OFFSET 0"
    ),
    "chat-messages by role": (
        "SELECT m.id FROM chat_messages m LEFT JOIN users u ON u.id = m.user_id "
        "WHERE 1=1 AND m.role = 'tool' ORDER BY m.id DESC LIMIT 50 OFFSET 0"
    ),
    "chat-messages by date range": (
        "SELECT m.id FROM chat_messages m WHERE 1=1 AND " + DAY_MSG + " >= '2026-01-01' AND " + DAY_MSG + " <= '2026-12-31' ORDER BY m.id DESC LIMIT 50"
    ),
    "chat-messages count with user filter": (
        "SELECT COUNT(*) FROM chat_messages m LEFT JOIN users u ON u.id = m.user_id WHERE 1=1 AND m.user_id = 3"
    ),
    "users list (limit page)": (
        "SELECT u.id, u.username, COALESCE(cud.request_count,0) FROM users u "
        "LEFT JOIN user_profiles p ON p.user_id = u.id "
        "LEFT JOIN chat_usage_daily cud ON cud.user_id = u.id AND cud.usage_date = '2026-09-14' "
        "WHERE 1=1 ORDER BY u.id DESC LIMIT 50 OFFSET 0"
    ),
    "users by username like": (
        "SELECT u.id FROM users u WHERE 1=1 AND u.username LIKE '%liu%' ESCAPE '\\' ORDER BY u.id DESC LIMIT 50"
    ),
    "users by type": "SELECT u.id FROM users u WHERE 1=1 AND u.type = 2 ORDER BY u.id DESC LIMIT 50",
    "planning by status": (
        "SELECT h.id FROM planning_history h LEFT JOIN users u ON u.id = h.user_id "
        "WHERE 1=1 AND h.status = 'FAILED' ORDER BY h.id DESC LIMIT 50"
    ),
    "planning by user+date": (
        "SELECT h.id FROM planning_history h WHERE 1=1 AND h.user_id = 5 AND " + DAY_PLAN + " >= '2026-01-01' ORDER BY h.id DESC LIMIT 50"
    ),
    "feedback unresolved": (
        "SELECT f.id FROM feedback f WHERE 1=1 AND f.resolved = 0 ORDER BY f.id DESC LIMIT 50"
    ),
    "credit rules by college": (
        "SELECT r.id FROM college_credit_rules r WHERE 1=1 AND r.college = '信息与通信工程学院' ORDER BY r.id ASC LIMIT 50"
    ),
    "credit activities by user": (
        "SELECT a.id FROM credit_activities a LEFT JOIN users u ON u.id = a.user_id "
        "WHERE 1=1 AND a.user_id = 3 ORDER BY a.id DESC LIMIT 50"
    ),
    "credit activities by verified": (
        "SELECT a.id FROM credit_activities a WHERE 1=1 AND a.verified = 0 ORDER BY a.id DESC LIMIT 50"
    ),
    "resources by type+quality": (
        "SELECT r.id FROM resources r WHERE 1=1 AND r.type = 'competition' AND r.data_quality = 'complete' ORDER BY r.id ASC LIMIT 50"
    ),
    "rag by embedding status": (
        "SELECT c.chunk_id FROM rag_chunks c WHERE 1=1 AND c.embedding_status = 'SUCCESS' ORDER BY c.chunk_id ASC LIMIT 50"
    ),
    "chat usage by date": (
        "SELECT d.id FROM chat_usage_daily d WHERE 1=1 AND d.usage_date = '2026-09-14' ORDER BY d.usage_date DESC LIMIT 50"
    ),
    "capability reference by section": (
        "SELECT r.id FROM capability_reference r WHERE 1=1 AND r.section = 'skill_mapping' ORDER BY r.id ASC LIMIT 50"
    ),
    "capability tags by category": (
        "SELECT t.name FROM capability_tags t WHERE 1=1 AND t.category = '技术能力' ORDER BY t.name ASC LIMIT 50"
    ),
}

print("database:", DB)
for name, sql in QUERIES.items():
    plan = con.execute("EXPLAIN QUERY PLAN " + sql).fetchall()
    details = " | ".join(row[3] for row in plan)
    scan = "SCAN" in details.upper() and "USING INDEX" not in details.upper() and "USING COVERING INDEX" not in details.upper()
    print(("FULL-SCAN " if scan else "indexed   ") + name)
    print("            " + details)
