# LifeComposer / 大学生成长规划 Agent

## 维护约定（重要）

> **更新本文件时**：在现有章节**顶部**插入新内容，**不要覆盖现有内容**。本文件是累积式的变更日志式文档，所有历史记录都应保留，新的开发成果、API、约定等依次追加在最前面。

---

## 项目定位

大学生成长规划助手后端。基于 Spring Boot 4.0.5 + SQLite + JdbcTemplate。

### 当前状态（2026-06-27 后）

基础后端服务器已完整搭建，包含：
- 用户系统（注册/登录/Session/Admin）
- 反馈系统（用户反馈 + 系统错误自动收集）
- 用户画像 API（`/api/profiles/me`）
- 目标管理 API（`/api/goals`）
- 规划历史 API（`/api/planning/history`）
- 问答 API（`/api/qa/health`、`/api/qa/ask`，mock fallback 模式）
- LLM 配置系统（按用途分组支持多 provider）
- LLM 客户端抽象（Fallback + OpenAI 兼容适配）
- 启动脚本 `run.sh`（环境变量加载，密钥安全）
- 68 个自动化测试，覆盖率完整

### 下一阶段目标（8 月主体开发）

- Agent 工作流打磨（提示词工程、工具链串联）
- 资源库建设（竞赛/课程/项目结构化数据）
- 能力画像 → 路径推荐算法

## 快速导航

### 目录结构

```
LifeComposer/                          # 当前 Spring Boot 后端
   ├── src/main/java/org/example/lifecomposer/
   │   ├── Controller/                 # REST + 页面路由
   │   ├── Service/                    # 业务逻辑 + LLM 客户端
   │   ├── Repository/                 # JdbcTemplate + SQL
   │   ├── Entity/                     # 普通 POJO (非 JPA Entity)
   │   ├── dto/                        # 请求体 DTO，含 LLM DTO
   │   ├── config/                     # 启动初始化 + LlmConfig（按用途LLM配置）
   │   ├── Security/                   # Spring Security 配置
   │   └── Exception/                  # 全局异常处理
   ├── src/test/java/                  # 测试代码
   │   └── org/example/lifecomposer/
   │       └── controller/             # BaseControllerTest + 各Controller测试
   ├── src/test/resources/
   │   └── application-test.properties # 测试隔离配置（使用 target/test-data.db）
   ├── external/
   │   ├── templates/                  # Thymeleaf 页面
   │   │   └── release_notes.html      # 发布说明（v0.0.1 起）
   │   └── static/                     # 静态资源
   ├── run.sh                          # 启动脚本（env 变量注入，无密钥）
   └── data.db                         # SQLite 数据文件 (自动创建，被 .gitignore)

.sisyphus/
   ├── plans/                          # 规划文件（纳入版本控制）
   ├── evidence/                       # 执行证据（被 .gitignore）
   └── notepads/                       # 学习记录

立项申请书2.0(1).pdf                   # 项目计划书 (开发参考)
立项申请书.md                          # 立项申请书全文摘要 (见本文件"立项信息"章节)
```

### 外部参考

原始项目（弃用）位于 `/Users/liuzy/Desktop/mess/雏雁计划/RepositoryDemo/`：
- 包含更多功能（论坛、地图点位、AI聊天、统计、安全提问），可作架构参考
- 使用全局 `static Connection`（已修复的 anti-pattern）
- Python AI 服务在 `external/ai_service.py`（Flask, port 8000, DeepSeek API）
- 前端同学的 `.html` 文件在 `external/templates/`

### 当前开发者

刘正扬 `liuzybj@bupt.edu.cn` — AI 辅助编程主导，Java/Spring 边用边学。

---

## 关键技术决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 数据库 | SQLite (`data.db`) | 轻量、零运维；Hibernate Community Dialect 适配 |
| ORM | JdbcTemplate（非 JPA） | 手动 SQL 控制，适合 AI 生成的模式 |
| 认证 | Session-based + Spring Security | 配合 Thymeleaf 管理端页面 |
| 密码 | BCrypt （通过 `PasswordEncoder`） | Spring Security 内置 |
| Java | 25 + Spring Boot 4.0.5 | 最新长期支持 |
| 构建 | Maven （`./mvnw`） | Wrapper 已包含，无需本地安装 |
| LLM 兼容层 | OkHttp + Gson (OpenAI 兼容协议) | 抽象出 `LlmClient`，支持 fallback / 云端切换 |
| LLM 配置 | 按用途分组（qa/planning/profile/sql） | 8 月模型对比实验时灵活替换 |

---

## 关键命令

```bash
# 启动后端 (port 18000，本地 mock 模式)
./mvnw spring-boot:run
# 或使用启动脚本（推荐，自动注入环境变量）
./run.sh

# 测试（使用 application-test.properties，独立数据库）
./mvnw test -Dspring.profiles.active=test

# 清理构建
./mvnw clean
```

---

## 架构注意事项

### 包命名风格
- 单数：`Controller`, `Service`, `Repository`, `Entity`, `Security`, `Exception`
- 不使用 `impl` 后缀，不使用接口分离（简单项目直写实现）
- **例外**：`LlmClient` 是接口，因为需要 Fallback + OpenAI 两种实现

### Repository 层
- 不使用 JPA `@Entity`，`@Repository` 中直接写 `JdbcTemplate` SQL
- 所有表通过 `createXxxTableIfNeeded()` 方法在 `DatabaseInitializer` 中自动创建
- 使用 `RowMapper` 手动映射 ResultSet → Entity
- 迁移逻辑在 `migrateUserSchema()` 中逐列 `PRAGMA table_info` 检测并 ALTER TABLE

### SQLite 注意事项
- `BOOLEAN` 用 `INTEGER` 存储（0/1），JDBC 驱动自动处理
- 不支持 `DROP COLUMN`（旧 SQLite），迁移代码已注释掉
- 支持 `PRAGMA table_info` 查询表结构
- 外键约束默认关闭，当前未启用

### 数据库初始化（默认管理员）
- `DatabaseInitializer` 在空库启动时自动插入默认管理员账户 `admin` / `admin`
- **这是预期行为**，用于本地开发便利。该账户在首次启动时自动创建，生产部署前应修改密码或禁用
- 当前项目处于开发阶段，暂时忽略默认管理员账户的安全隐患

### Exception 层
- `GlobalExceptionHandler` 会截获所有 404/500 并自动记录到 feedback 表
- 内置 Bot 请求检测（空 User-Agent / 爬虫关键词 → 403）
- 大量已知扫描路径静默忽略（`IGNORED_NOT_FOUND_URLS` 列表）
- API 请求返回纯 JSON，页面请求重定向到 `/error/{code}`

### API 端点前缀
- 用户：`/api/users/*`
- 反馈：`/api/feedback/*`
- 画像：`/api/profiles/*`
- 目标：`/api/goals/*`
- 历史：`/api/planning/*`
- 问答：`/api/qa/*`
- 页面路由：通过 `HomeController` 的 `@Controller` 映射

### Security 配置
- 公开端点：`/api/users/register`, `/api/users/login`, `/api/feedback/submit`, `/api/feedback/system-error`, `/api/qa/health`
- 需要 ADMIN 角色：反馈管理、用户管理、封禁操作
- 需要认证：`/api/users/current`, `/api/users/logout`, `/api/profiles/**`, `/api/goals/**`, `/api/planning/**`, `/api/qa/ask`
- JSESSIONID Cookie 传递认证状态
- CORS 已限制为本地开发环境（`http://localhost:*`, `http://127.0.0.1:*`, `https://localhost:*`）

### LLM 配置层
- 按用途分组，每组字段：`provider`, `baseUrl`, `model`, `apiKeyEnv`, `enabled`, `timeoutMillis`, `temperature`
- 默认全部指向本地 Ollama `http://localhost:11434/v1`，模型 `lfm2.5:8b`，`enabled=false`
- `LlmClientFactory`：根据 `useCase` 名称路由到对应客户端
- `FallbackLlmClient`：`enabled=false` 或网络异常时兜底，返回 mock 响应
- `OpenAiCompatibleLlmClient`：OkHttp 适配器，支持任何 OpenAI 兼容接口
- API Key 读取方式：从 `apiKeyEnv` 指定的环境变量读取，代码和日志中**永不出现**真实 key

### 测试基础设施
- 测试基类：`BaseControllerTest`（MockMvc + SecurityMockMvc + 独立测试 DB）
- 隔离数据库：`target/test-data.db`（通过 `application-test.properties` 配置）
- **生产数据永不污染**：测试不触碰 `data.db`，启动时通过 `before/after` 时间戳验证
- 全量测试 68 个，通过 `./mvnw test -Dspring.profiles.active=test` 一键运行

### Run Script (`run.sh`)
- 所有 LLM 配置通过环境变量注入：`LLM_QA_PROVIDER`, `LLM_QA_BASE_URL`, `LLM_QA_MODEL`, `LLM_QA_API_KEY_ENV`, `LLM_QA_ENABLED`（四个用途重复此模式）
- 默认值：`provider=ollama`, `baseUrl=http://localhost:11434/v1`, `model=lfm2.5:8b`, `enabled=false`
- 切换到真实 LLM：设置 `LLM_QA_ENABLED=true` 并在 shell 中 `export LLM_QA_API_KEY_ENV=DEEPSEEK_API_KEY`（或对应的环境变量键名）

---

## 开发风格约定

1. **构造函数注入**（非 `@Autowired` 字段注入）
2. **Service 抛出 `IllegalStateException`** 用于业务错误，Controller 捕获后返回 400
3. **Repository 返回 `boolean` / `int`** 表示操作结果，不抛运行时异常
4. **DTO 使用 Lombok**（`@Getter @Setter`），Entity 同理
5. **`@Valid` 校验**在 Controller 参数上，`RegisterRequest`/`LoginRequest` 已配置 `@NotBlank` + `@Size`
6. **Fix 注释风格**：`// FIX: explanation` 记录 AI 识别并修复的老代码问题
7. **日志使用 Log4j2**（Spring Boot 默认 Logback 已被排除）
8. **所有权检查**：任何访问用户私有数据（profile/goals/planning）的端点**必须**通过 SecurityContext 解析当前用户并验证归属，禁止跨用户读取

---

## 数据库表

### 核心表
| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `users` | 用户 | id, username, password_hash, type(USER=1/ADMIN=2), is_banned, ban_end_time |
| `feedback` | 反馈 | id, user_id, content, type(system\|user), resolved |
| `user_profiles` | 用户画像 | id, user_id(UNIQUE), college, major, grade, student_id, skills_json, interests_json, experiences_json, preferences_json |
| `goals` | 目标 | id, user_id, title, description, category, priority, status(ACTIVE/PAUSED/COMPLETED/ARCHIVED), target_date, progress |
| `planning_history` | AI 交互历史 | id, user_id, type(QA/PLAN/PROFILE_ANALYSIS), request_json, response_json, provider, model, status(MOCKED/SUCCESS/FAILED), error_message |

### 待建表（后续开发）
| 表名 | 说明 |
|------|------|
| `college_credit_rules` | 加分规则（竞赛/讲座/课程/项目等） |
| `credit_activities` | 用户加分记录 |

---

## 已有 API 清单

### 用户管理
| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/users/register` | 无 | 注册（用户名3-20字符, 密码1-50） |
| POST | `/api/users/login` | 无 | 登录，设置 session |
| POST | `/api/users/logout` | 已登录 | 登出，清除 session |
| GET | `/api/users/current` | 已登录 | 获取当前用户信息 |
| GET | `/api/users/all` | ADMIN | 获取所有用户 |
| PUT | `/{id}/grant-admin` | ADMIN | 授予管理员 |
| PUT | `/{id}/revoke-admin` | ADMIN | 撤销管理员 |
| PUT | `/{id}/ban` | ADMIN | 封禁（时间格式：`1d`, `30m`, `1y`，`0` 为永久）|
| PUT | `/{id}/unban` | ADMIN | 解封 |
| POST | `/admin/reset-password/{id}` | ADMIN | 重置密码为 000000 |

### 反馈管理
| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/feedback/submit` | 无 | 用户反馈 |
| POST | `/api/feedback/system-error` | 无 | 系统错误报告 |
| GET | `/api/feedback/all` | ADMIN | 所有反馈 |
| GET | `/api/feedback/type/{type}` | ADMIN | 按类型过滤 |
| POST | `/{id}/resolve` | ADMIN | 标记已解决 |

### 用户画像
| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/profiles/me` | 已登录 | 获取当前用户画像 |
| PUT | `/api/profiles/me` | 已登录 | Upsert 当前用户画像 |

### 目标管理
| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/goals` | 已登录 | 创建目标 |
| GET | `/api/goals` | 已登录 | 列出当前用户的目标 |
| GET | `/api/goals/{id}` | 已登录 | 目标详情（仅本人） |
| PUT | `/api/goals/{id}` | 已登录 | 更新目标（仅本人） |
| DELETE | `/api/goals/{id}` | 已登录 | 软归档（status=ARCHIVED，仅本人） |

### 规划历史
| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/planning/history` | 已登录 | 当前用户的 AI 交互历史 |
| GET | `/api/planning/history/{id}` | 已登录 | 历史详情（仅本人） |

### 问答
| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/qa/health` | 无 | LLM 健康检查（返回 provider/model/enabled 状态） |
| POST | `/api/qa/ask` | 已登录 | 提问，返回回答和 historyId |

### 页面路由
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 首页 |
| GET | `/login` | 登录页 |
| GET | `/admin` | 管理后台 (ADMIN) |
| GET | `/admin/user` | 用户管理页 (ADMIN) |
| GET | `/admin/feedback_management` | 反馈管理页 (ADMIN) |

---

## 立项信息

> 详见 [`立项申请书.md`](./立项申请书.md)（全文摘要）

### 项目概况

| 项目 | 内容 |
|------|------|
| 名称 | 基于能力画像的大学生跨学科成长路径规划智能体 |
| 类型 | 大学生创新创业训练计划 — 创新训练类（自主探索类） |
| 依托学院 | 计算机学院（国家示范性软件学院） |
| 指导教师 | 陆天波（教授，网络安全学院副院长）、金昕（工程师） |
| 项目负责人 | 吕芷涵 |
| 团队成员 | 刘正扬（后端+Agent）、陆子粤（数据）、王钰博（画像+推荐）、高铭阳（前端+测试）、吕芷涵（前端+统筹） |

### 项目目标

> 不是推荐竞赛，而是帮助学生从自身能力、兴趣和目标出发，判断适合尝试的成长方向，生成可理解、可执行的学习路径和资源建议。

系统以 **App 或 Web App Demo** 形式呈现，核心流程：**能力画像 → 方向识别 → 路径生成 → 资源适配 → Agent 建议**。

### 核心模块（规划中）

| 模块 | 说明 | 当前状态 |
|------|------|----------|
| 用户画像与目标输入 | 专业/年级/技能/兴趣/经历/目标 → 能力标签 | **接口已就绪，待前端对接** |
| 资源建模 | 竞赛/项目/课程 → 结构化标签库 + 向量化 | 未开始 |
| 适配评估 | 标签匹配 + 文本相似度 + 加权评分 | 未开始 |
| **成长规划 Agent** | 工作流式 Agent：目标理解 → 工具调用 → 路径规划 | **基础 API 就绪，8月打磨** |
| 反馈更新 | 根据用户行为迭代优化画像和推荐 | 未开始 |
| 互勉与提醒 | 组队推荐、阶段提醒、同伴互勉 | 后续增强 |

### 技术路线

- **后端**：Spring Boot 4.0.5 + SQLite + JdbcTemplate ✅ 已搭建
- **LLM 接入**：OpenAI 兼容协议（OkHttp）✅ 已支持多 provider 切换
- **本地测试用 LLM**：Ollama `lfm2.5:8b`（默认，无需 key 即可启动）
- **云端 LLM**：DeepSeek/Qwen(GLM)/GPT(Longxia)，配置 API key 即可
- **Agent**：工作流式，待 8 月打磨
- **前端**：待定（App / Web）

### 进度规划

| 阶段 | 时间 | 内容 |
|------|------|------|
| 当前 | 2026-06-27 | **v0.0.1 后端基础服务器完成** ✅ |
| 主体开发 | 2026-08 | Agent 提示词打磨 + 资源库建设 + 适配算法 |
| 后续 | 持续 | 问卷/访谈、系统测试、反馈优化、互勉提醒 |

### 与当前代码的关系

当前 v0.0.1 已搭建的项目基础设施层，覆盖了原计划中的多项：

| 原计划 | 当前状态 |
|--------|----------|
| 扩展 `user_profiles` 表（已有 SCHEMA 设计） | ✅ v0.0.1 已建表 + 画像 API |
| 实现 `college_credit_rules` 和 `credit_activities` 表 | ❌ 待 8 月主体开发 |
| 实现 `planning_history` 和 `goals` 表 | ✅ v0.0.1 已建表 + API |
| 接入 DeepSeek API，实现 Agent 问答 | ✅ v0.0.1 LLM 客户端抽象已就绪，Q&A mock API 已就绪；8 月接入真实 Agent |
| 前端开发（使用当前 external/templates/ 或全新框架） | ❌ 待前端同学启动 |

### 额外信息

1. 在每次开发结束之后，当我指出需要更新release note时，请根据之前的开发内容，更新[release_notes.html](LifeComposer/external/templates/release_notes.html)(如果没有则创建文件并撰写)
   - 示例文件：[release_notes_example.html](LifeComposer/external/templates/release_notes_example.html)
   - 要求格式尽量统一。如果需要新的样式，可以添加，但需要在更新后向我汇报
2. 现在可以使用的LLM api有：deepseek、qwen（百炼模型）、glm、gpt（Longxia api）、ollama本地模型（qwen 3.5:9b, lfm2.5:8b），api key在环境变量中，请不要阅读，需要api key时指出填写位置，由我手动填写

---

## 安全约束

> 以下约束不可违反。

- **永不把 API key 写进代码、配置文件、测试、日志或文档**
- **永不提交 `data.db` 到 Git**
- **永不在 `run.sh` 以外读取或打印 API key**
- **永不读取或泄露 `/Users/liuzy/.config/opencode/opencode.jsonc` 中的密钥值**

---

## 文件验证清单

```bash
# Java 编译检查
./mvnw compile

# 测试（68 个）
./mvnw test -Dspring.profiles.active=test

# 启动验证（本地 mock 模式）
./run.sh

# 测试 API
curl -s http://localhost:18000/api/users/current
curl -s http://localhost:18000/api/qa/health
```

---

## 历史版本

| 版本 | 日期 | 主要内容 |
|------|------|----------|
| v0.0.1 | 2026-06-27 | 初始后端服务器基础（用户/反馈/画像/目标/历史/问答/LLM 抽象） |

---

## 历史提交记录

```
f831103 — Fixed import issues
7fb1f4b — Basic user management setup
257cdf6 — Fix 404 browser download by redirecting non-API not-found to error page
506d700 — Fix /error mapping conflict and add global exception handler
6425c17 — Migrate login page and error handling to LifeComposer
```

---

*Archived from original project at `/Users/liuzy/Desktop/mess/雏雁计划/RepositoryDemo/`*
