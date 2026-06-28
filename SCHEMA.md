# 数据库架构设计（Schema Design）

## 概述

本系统面向北邮大学生成长规划场景，基于 SQLite 数据库存储。当前设计包含 7 张核心表：

### 已实现（v0.0.1）

| 表名 | 用途 |
|------|------|
| `users` | 用户账户 |
| `feedback` | 用户反馈与系统错误 |
| `user_profiles` | 用户画像扩展 |
| `planning_history` | AI 交互历史记录 |
| `goals` | 用户目标设定 |

### 待建（8月主体开发）

| 表名 | 用途 |
|------|------|
| `college_credit_rules` | 加分规则标准（双创分 + 保研加分） |
| `credit_activities` | 用户已获得的加分记录 |

> **注意**：SQLite 当前 `PRAGMA foreign_keys = OFF`（默认），表之间的 `REFERENCES` 约束仅作为逻辑关联标注，运行时不强制。

---

## 关系总览（逻辑关联）

```
users
  ├─ 1:1 ── user_profiles     （按 user_id 关联）
  ├─ 1:N ── feedback          （按 user_id 关联）
  ├─ 1:N ── planning_history  （按 user_id 关联）
  └─ 1:N ── goals             （按 user_id 关联）

college_credit_rules
  └─ 1:N ── credit_activities （按 rule_id 可选关联）
```

---

## 各表详细设计

### 1. `users`（用户表）✅ 已实现

存储用户账户信息。

```sql
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  type INTEGER NOT NULL DEFAULT 1 CHECK(type IN (1,2)),
  is_banned BOOLEAN NOT NULL DEFAULT 0,
  ban_end_time TIMESTAMP NULL,
  avatar TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**字段说明：**

| 字段 | 必填 | 说明 | 示例 |
|------|------|------|------|
| `id` | 自动 | 主键 | 1 |
| `username` | 是 | 用户名，唯一 | 'alice' |
| `password_hash` | 是 | BCrypt 哈希密码 | `$2a$10$...` |
| `type` | 是 | 用户类型：1=USER, 2=ADMIN | 1 |
| `is_banned` | 是 | 是否封禁（0=正常，1=封禁） | 0 |
| `ban_end_time` | 否 | 封禁结束时间（毫秒时间戳），null 表示永久或未封禁 | 1719500000000 |
| `avatar` | 否 | 头像图片路径或 URL | null |
| `created_at` | 自动 | 创建时间 | 2026-06-27 10:00:00 |
| `updated_at` | 自动 | 更新时间 | 2026-06-27 10:00:00 |

---

### 2. `feedback`（反馈表）✅ 已实现

存储用户提交反馈和系统自动收集的错误报告。

```sql
CREATE TABLE IF NOT EXISTS feedback (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NULL,
  username TEXT NULL,
  content TEXT NOT NULL,
  type TEXT NOT NULL,
  url TEXT NULL,
  user_agent TEXT NULL,
  stack_trace TEXT NULL,
  create_time TIMESTAMP NOT NULL,
  resolved BOOLEAN NOT NULL DEFAULT 0,
  resolved_by TEXT NULL,
  resolved_time TIMESTAMP NULL
);
```

**字段说明：**

| 字段 | 必填 | 说明 | 示例 |
|------|------|------|------|
| `id` | 自动 | 主键 | 1 |
| `user_id` | 否 | 关联用户（未登录时可为 NULL） | 5 |
| `username` | 否 | 冗余存储用户名（提交时的快照） | 'alice' |
| `content` | 是 | 反馈内容/错误信息 | '页面加载失败' |
| `type` | 是 | 类型：'user'=用户反馈，'system'=系统错误 | 'system' |
| `url` | 否 | 触发错误的 URL（系统错误时自动记录） | '/api/goals/123' |
| `user_agent` | 否 | 请求 User-Agent（用于 Bot 检测与问题排查） | 'Mozilla/5.0...' |
| `stack_trace` | 否 | 错误堆栈信息（仅系统错误） | 'java.lang.NullPointerException...' |
| `create_time` | 是 | 创建时间（代码传入，非自动填充） | 2026-06-27 10:00:00 |
| `resolved` | 是 | 是否已解决（BOOLEAN, 0=未解决，1=已解决） | 0 |
| `resolved_by` | 否 | 处理人（管理员用户名） | 'admin' |
| `resolved_time` | 否 | 解决时间 | 2026-06-28 14:00:00 |

---

### 3. `user_profiles`（用户画像表）✅ 已实现

与 `users` 表 1:1 逻辑关联，存储用户画像扩展信息。JSON 字段应对灵活扩展需求。

```sql
CREATE TABLE IF NOT EXISTS user_profiles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL UNIQUE,
    college TEXT,
    major TEXT,
    grade TEXT,
    student_id TEXT,
    skills_json TEXT,
    interests_json TEXT,
    experiences_json TEXT,
    preferences_json TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
```

**字段说明：**

| 字段 | 说明 | 示例 |
|------|------|------|
| `user_id` | 关联用户，`UNIQUE` 约束保证 1:1 | 1 |
| `college` | 学院 | '计算机学院' |
| `major` | 专业 | '软件工程' |
| `grade` | 年级（文本存储，如"大一"或入学年份） | '大一' 或 '2023' |
| `student_id` | 学号 | '2023210123' |
| `skills_json` | 技能列表（JSON 数组） | `["Python","Java"]` |
| `interests_json` | 兴趣方向（JSON 数组） | `["人工智能","嵌入式"]` |
| `experiences_json` | 经历记录（JSON 数组） | `["电子设计竞赛"]` |
| `preferences_json` | 用户偏好（JSON 对象） | `{"theme":"dark"}` |
| `created_at` | 创建时间（自动，datetime 函数） | 2026-06-27 10:00:00 |
| `updated_at` | 更新时间（自动，datetime 函数） | 2026-06-27 10:00:00 |

---

### 4. `planning_history`（AI 交互历史表）✅ 已实现

存储用户与 AI 的交互历史，包括问答、规划、画像分析等类型。

```sql
CREATE TABLE IF NOT EXISTS planning_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    type TEXT NOT NULL,
    request_json TEXT,
    response_json TEXT,
    provider TEXT,
    model TEXT,
    status TEXT NOT NULL DEFAULT 'MOCKED',
    error_message TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
```

**字段说明：**

| 字段 | 说明 | 示例 |
|------|------|------|
| `user_id` | 关联用户 | 1 |
| `type` | 交互类型（自由文本，常见值见下表） | 'QA' |
| `request_json` | 请求内容（JSON 字符串，可为空） | `{"question":"适合参加什么竞赛"}` |
| `response_json` | 响应内容（JSON 字符串，可为空） | `{"answer":"建议参加..."}` |
| `provider` | LLM 提供商 | 'deepseek', 'ollama' |
| `model` | 使用的模型名称 | 'lfm2.5:8b' |
| `status` | 执行状态 | 'MOCKED'（未启用LLM时默认）、'SUCCESS'、'FAILED' |
| `error_message` | 失败时的错误信息 | null 或 'Timeout' |
| `created_at` | 创建时间（自动，datetime 函数） | 2026-06-27 10:00:00 |

> **`type` 取值说明**：代码无 CHECK 约束，常见约定值为 `'QA'`（问答）、`'PLAN'`（规划）、`'PROFILE_ANALYSIS'`（画像分析）。

---

### 5. `goals`（目标设定表）✅ 已实现

用户设定的短期/长期目标，供规划引擎使用。独立于规划历史，可跨多次规划持续追踪。

```sql
CREATE TABLE IF NOT EXISTS goals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    category TEXT,
    priority TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    target_date TEXT,
    progress INTEGER DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
```

**字段说明：**

| 字段 | 说明 | 示例 |
|------|------|------|
| `user_id` | 关联用户 | 1 |
| `title` | 目标标题 | '参加数学建模竞赛' |
| `description` | 目标描述 | null 或详细说明 |
| `category` | 目标类别（代码无 CHECK 约束，常见值如下） | 'academic' |
| `priority` | 优先级（代码无 CHECK 约束，常见值：'high'/'medium'/'low'） | 'medium' |
| `status` | 状态（代码无 CHECK 约束，常见值：'ACTIVE'/'PAUSED'/'COMPLETED'/'ARCHIVED'） | 'ACTIVE' |
| `target_date` | 目标期限 | '2027-06-30' |
| `progress` | 进度百分比（建议 0~100，代码无硬性约束） | 50 |
| `created_at` | 创建时间（自动，datetime 函数） | 2026-06-27 10:00:00 |
| `updated_at` | 更新时间（自动，datetime 函数） | 2026-06-27 10:00:00 |

> **约束说明**：代码中 `category`、`priority`、`status`、`progress` 均未使用 CHECK 约束。取值约定由 Java 层控制。

---

### 6. `college_credit_rules`（加分规则表）📋 待建

记录双创分和保研加分的所有标准规则。双创分和保研加分共用此表，通过 `credit_type` 区分。

```sql
CREATE TABLE college_credit_rules (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  college           TEXT NOT NULL,
  credit_type       TEXT NOT NULL CHECK(credit_type IN ('graduation', 'recommendation')),
  category          TEXT NOT NULL CHECK(category IN ('competition', 'lecture', 'course', 'project', 'paper', 'patent', 'sports', 'arts', 'veteran')),
  comp_level        TEXT CHECK(comp_level IN ('S', 'A+', 'A', 'B+', 'B', 'national', 'provincial', 'school')),
  comp_name         TEXT,
  award_tier        TEXT CHECK(award_tier IN ('first', 'second', 'third', 'special', 'participation')),
  credits           REAL NOT NULL,
  category_cap      REAL,
  team_formula      TEXT,
  student_cohort    TEXT,
  doc_source        TEXT,
  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**字段说明：**

| 字段 | 必填 | 说明 | 示例 |
|------|------|------|------|
| `id` | 自动 | 主键 | 1 |
| `college` | 是 | 学院名称 | '信通院', '计算机学院' |
| `credit_type` | 是 | 加分类型: `graduation`=双创分, `recommendation`=保研加分 | 'recommendation' |
| `category` | 是 | 来源类别 | 'competition', 'lecture'(仅双创分), 'course'(仅双创分) |
| `comp_level` | 否 | 竞赛级别（非竞赛类 NULL） | 'S', 'A+', 'B', 'national' |
| `comp_name` | 否 | 竞赛/活动具体名称 | '全国大学生电子设计竞赛' |
| `award_tier` | 否 | 奖项等级（非竞赛类 NULL） | 'first', 'second', 'third' |
| `credits` | 是 | 对应分值 | 2.5, 0.2 |
| `category_cap` | 否 | 该类别的总学分上限 | 2.0（双创课上限） |
| `team_formula` | 否 | 团队赛加分公式说明 | '系数 = 1 + (x-1) × 0.6' |
| `student_cohort` | 否 | 适用年级/学生群体 | '2023' |
| `doc_source` | 否 | 来源文件名称 | '信通院创新实践活动加分办法_2023级起适用.pdf' |
| `created_at` | 自动 | 记录创建时间 | 2026-06-11 12:00:00 |

---

### 7. `credit_activities`（用户加分记录表）📋 待建

记录用户已获得的各类加分经历。`rule_id` 可选关联规则表，便于先记录后匹配。

```sql
CREATE TABLE credit_activities (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id           INTEGER NOT NULL,
  rule_id           INTEGER,
  credit_type       TEXT NOT NULL CHECK(credit_type IN ('graduation', 'recommendation')),
  category          TEXT NOT NULL,
  comp_name         TEXT,
  comp_level        TEXT,
  award_tier        TEXT,
  credits           REAL NOT NULL,
  obtained_date     TEXT,
  certificate_ref   TEXT,
  verified          INTEGER DEFAULT 0,
  notes             TEXT,
  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ca_user ON credit_activities(user_id);
CREATE INDEX idx_ca_type ON credit_activities(credit_type);
```

**字段说明：**

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | 自动 | 主键 |
| `user_id` | 是 | 关联 `users(id)` |
| `rule_id` | 否 | 关联 `college_credit_rules(id)`，可先录后关联 |
| `credit_type` | 是 | 冗余字段，直接从规则继承或手动填写 |
| `category` | 是 | 冗余字段 |
| `comp_name` | 否 | 竞赛/活动名称 |
| `comp_level` | 否 | 竞赛级别 |
| `award_tier` | 否 | 奖项 |
| `credits` | 是 | 实际获得的加分值 |
| `obtained_date` | 否 | 获奖/完成日期 |
| `certificate_ref` | 否 | 证书或证明文件路径 |
| `verified` | 否 | 审核状态: 0=未审核, 1=已审核 |
| `notes` | 否 | 备注 |

> **冗余设计说明**：`credit_type`、`category`、`comp_level`、`award_tier` 在活动表中冗余存储，确保规则表内容变更时不会影响用户已有记录。

---

## 建表顺序

由于存在外键依赖，建表应遵循以下顺序：

```
1. users
2. feedback
3. college_credit_rules（无外部依赖）
4. user_profiles（依赖 users）
5. credit_activities（依赖 users + college_credit_rules（可选））
6. planning_history（依赖 users）
7. goals（依赖 users）
```

> 上述建表逻辑在 `DatabaseInitializer.init()` 方法中通过 `createXxxTableIfNeeded()` 逐步执行。

---

## 附录：SQLite 兼容性说明

- `BOOLEAN` 用 `INTEGER`（0/1）存储
- `CHECK` 约束用于枚举值校验（`credit_type`, `category`, `comp_level` 等），AI 录入时可减少非法数据
- `REFERENCES` 外键语法会被 SQLite 解析但默认不强制执行（`PRAGMA foreign_keys = OFF`）
- `TEXT` 字段可存储 JSON，使用 `json_extract()` 函数查询
- 索引仅建在查询频繁的列（user_id, credit_type），避免过度索引
- `datetime('now')` 和 `CURRENT_TIMESTAMP` 均可作为默认值，前者为 TEXT 格式，后者为 TIMESTAMP 格式，SQLite 两者兼容

---

*设计日期：2026-06-11*
*更新日期：2026-06-28*
*参考文件：信通院创新实践活动加分办法_2023级起适用.pdf*
*实际代码：LifeComposer/src/main/java/org/example/lifecomposer/Repository/*.java*
