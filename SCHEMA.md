# 数据库架构设计（Schema Design）

## 概述

本系统面向北邮大学生成长规划场景，基于 SQLite 数据库存储。当前设计包含 5 张核心表：

| 表名 | 用途 | 优先级 |
|------|------|--------|
| `college_credit_rules` | 加分规则标准（双创分 + 保研加分） | 核心 |
| `credit_activities` | 用户已获得的加分记录 | 核心 |
| `user_profiles` | 用户画像扩展 | 核心 |
| `planning_history` | 规划历史记录 | 核心 |
| `goals` | 用户目标设定 | 推荐 |

---

## 关系总览

```
users (已有)
  ├── 1:1 ── user_profiles
  ├── 1:N ── credit_activities
  ├── 1:N ── planning_history
  └── 1:N ── goals

college_credit_rules
  └── 1:N ── credit_activities (rule_id 可选关联)
```

---

## 各表详细设计

### 1. `college_credit_rules`（加分规则表）

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

### 2. `credit_activities`（用户加分记录表）

记录用户已获得的各类加分经历。`rule_id` 可选关联规则表，便于先记录后匹配。

```sql
CREATE TABLE credit_activities (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id           INTEGER NOT NULL REFERENCES users(id),
  rule_id           INTEGER REFERENCES college_credit_rules(id),
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

### 3. `user_profiles`（用户画像表）

与 `users` 表 1:1 关联，存储用户画像扩展信息。结构化字段用于常用查询，`tags` JSON 字段应对灵活扩展需求。

```sql
CREATE TABLE user_profiles (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id           INTEGER UNIQUE NOT NULL REFERENCES users(id),
  college           TEXT,
  major             TEXT,
  grade             INTEGER,
  student_id        TEXT,
  tags              TEXT,
  preferences       TEXT,
  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**字段说明：**

| 字段 | 说明 | 示例 |
|------|------|------|
| `user_id` | 关联用户，`UNIQUE` 约束保证 1:1 | 1 |
| `college` | 学院 | '信息与通信工程学院' |
| `major` | 专业 | '通信工程' |
| `grade` | 入学年份 | 2023 |
| `student_id` | 学号 | '2023210123' |
| `tags` | JSON 灵活标签，见下方示例 | `{"interests":[...]}` |
| `preferences` | JSON 用户偏好设置 | `{"theme":"dark"}` |

**`tags` 字段 JSON 示例：**

```json
{
  "interests": ["人工智能", "嵌入式开发", "音乐"],
  "skills": ["Python", "Java", "C"],
  "target_companies": ["华为", "字节跳动"],
  "weaknesses": ["英语", "数据结构"],
  "learning_style": "hands-on"
}
```

---

### 4. `planning_history`（规划历史记录表）

存储 AI 生成的成长规划历史，供用户回溯和比较。

```sql
CREATE TABLE planning_history (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id           INTEGER NOT NULL REFERENCES users(id),
  title             TEXT,
  content           TEXT NOT NULL,
  ai_model          TEXT,
  prompt_snapshot   TEXT,
  user_feedback     TEXT,
  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ph_user ON planning_history(user_id);
```

**字段说明：**

| 字段 | 说明 |
|------|------|
| `title` | 规划主题，如"大二上学期成长计划" |
| `content` | 规划全文（Markdown 或 JSON） |
| `ai_model` | 生成时使用的 AI 模型名称，用于后续评估 |
| `prompt_snapshot` | 生成时的 prompt 快照，方便复现和调试 |
| `user_feedback` | 用户对规划质量的反馈文本 |

---

### 5. `goals`（目标设定表）

用户设定的短期/长期目标，供规划引擎使用。独立于规划历史，可跨多次规划持续追踪。

```sql
CREATE TABLE goals (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id           INTEGER NOT NULL REFERENCES users(id),
  title             TEXT NOT NULL,
  category          TEXT CHECK(category IN ('academic', 'career', 'skill', 'health')),
  target_date       TEXT,
  priority          TEXT DEFAULT 'medium' CHECK(priority IN ('high', 'medium', 'low')),
  status            TEXT DEFAULT 'active' CHECK(status IN ('active', 'completed', 'abandoned')),
  progress          INTEGER DEFAULT 0 CHECK(progress >= 0 AND progress <= 100),
  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_goals_user ON goals(user_id);
```

**字段说明：**

| 字段 | 说明 | 示例 |
|------|------|------|
| `category` | 目标类别 | 'academic'(学业), 'career'(职业), 'skill'(技能), 'health'(健康) |
| `target_date` | 目标期限 | '2027-06-30' |
| `priority` | 优先级 | 'high', 'medium', 'low' |
| `status` | 状态 | 'active'(进行中), 'completed'(已完成), 'abandoned'(放弃) |
| `progress` | 进度百分比 | 0 ~ 100 |

---

## 建表顺序

由于存在外键依赖，建表应遵循以下顺序：

```
1. users（已有，不变）
2. college_credit_rules（无外部依赖）
3. user_profiles（依赖 users）
4. credit_activities（依赖 users + college_credit_rules（可选））
5. planning_history（依赖 users）
6. goals（依赖 users）
```

> 上述建表逻辑应添加到 `DatabaseInitializer.init()` 方法中。

---

## 附录：SQLite 兼容性说明

- `BOOLEAN` 用 `INTEGER`（0/1）存储
- `CHECK` 约束用于枚举值校验（`credit_type`, `category`, `comp_level` 等），AI 录入时可减少非法数据
- `REFERENCES` 外键语法会被 SQLite 解析但默认不强制执行（`PRAGMA foreign_keys = OFF`）
- `TEXT` 字段可存储 JSON，使用 `json_extract()` 函数查询
- 索引仅建在查询频繁的列（user_id, credit_type），避免过度索引

---

*设计日期：2026-06-11*
*参考文件：信通院创新实践活动加分办法_2023级起适用.pdf*
