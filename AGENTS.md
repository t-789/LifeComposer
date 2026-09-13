# LifeComposer / 大学生成长规划 Agent

## 项目定位

大学生成长规划助手后端。基于 Spring Boot 4.0.5 + SQLite + JdbcTemplate。

### 当前状态（2026-09-13 v0.0.5 后）

基础后端 + 数据底座 + Agent tool-use 已搭建完成，包含：
- 用户系统（注册/登录/Session/Admin）
- 反馈系统（用户反馈 + 系统错误自动收集）
- 用户画像 API（`/api/profiles/me`，含 availableTime / goals 字段）
- 规划历史 API（`/api/planning/history`）
- 问答 API（`/api/qa/health`、`/api/qa/ask`；qa 仍保留可配置 fallback）
- AI 对话 API（`/api/chat/send`、`/api/chat/stream` SSE、`/api/chat/history`、`/api/chat/context`）
- 成长数据底座（6 张表：college_credit_rules、credit_activities、resources、rag_chunks、capability_tags、capability_reference）
- **应用安全加固（Milestone 5）**：CSRF（XSRF-TOKEN Cookie + X-XSRF-TOKEN 头 + `/api/csrf`）、Session Cookie HttpOnly/SameSite=Lax、登录后重建 Session、显式 CORS 来源
- **AI 使用控制**：每用户 5 次/分钟 + 100 次/天（Asia/Shanghai），新增 `chat_usage_daily` 用量表；超限 429；`max_tokens` 强制 1024；管理员可重置当日额度并写审计日志
- **基础防护**：注册/登录限速与失败临时锁定、SSE 超时可配置、输入长度限制、管理员 API 可选内网限制
- **独立数据导入 CLI**（`DataImportCli` + `import.sh`，`--import-dir` 执行完即退出，不启动 Web、不初始化默认管理员）
- **Ollama embedding + RAG 检索**（rag_chunks 7 个新字段 + `RagSearchService`；topK=5、minSimilarity=0.2）
- **Agent 多轮 tool-use**（5 个白名单只读工具 + ToolRegistry + AgentOrchestrator，最多 8 轮）
- **SSE 流式过程展示**（thinking 计时 / tool_call / tool_result / token / assistant_message / error / done）
- 生成式 LLM 默认统一切换 DeepSeek `deepseek-flash`；`/api/chat/*` 不使用 fallback（503 / SSE error）
- 启动脚本 `run.sh`（环境变量加载，密钥安全）
- 188 个自动化测试，全部通过

### 下一阶段目标

- 能力画像 → 路径推荐算法（skill_mapping 归一 + skill_profiles 展开 + 标签差集匹配）
- 前端从 `chat_test.html` 推广到正式聊天页面
- RAG 检索效果回归（`样例/rag/test_questions.json` 夹具）与资源库持续扩充

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
   │   ├── config/                     # 启动初始化 + LlmConfig / EmbeddingConfig / RagConfig / AsyncConfig
   │   ├── agent/                      # AgentOrchestrator + ToolRegistry + tools/（5 个只读工具）
   │   ├── embedding/                  # OllamaEmbeddingClient（独立于生成式 LLM）
   │   ├── rag/                        # RagSearchService + RagHit
   │   ├── importer/                   # DataImportCli + DataImportService（独立 CLI）
   │   ├── Security/                   # Spring Security 配置（@ConditionalOnWebApplication）
   │   └── Exception/                  # 全局异常处理
   ├── src/test/java/                  # 测试代码
   │   └── org/example/lifecomposer/
   │       └── controller/             # BaseControllerTest + 各Controller测试
   ├── src/test/resources/
   │   └── application-test.properties # 测试隔离配置（使用 target/test-data.db）
   ├── external/
   │   ├── templates/                  # Thymeleaf 页面
   │   │   └── release_notes.html      # 发布说明（v0.0.1 起）
   │   └── static/                     # 静态资源（apiList.txt 等）
   ├── run.sh                          # 启动脚本（env 变量注入，无密钥）
   ├── import.sh                       # 独立数据导入 CLI 包装脚本
   └── data.db                         # SQLite 数据文件 (自动创建，被 .gitignore)

.omo/
   ├── plans/                          # 规划文件（纳入版本控制）
   ├── evidence/                       # 执行证据（被 .gitignore）
   └── notepads/                       # 学习记录

样例/                                  # 数据交付包（2026-09-05 快照：resources/画像/RAG切片/能力字典/加分规则提取）
立项申请书2.0(1).pdf                   # 项目计划书 (开发参考)
立项申请书.md                          # 立项申请书全文摘要
SCHEMA.md                            # 数据库表结构
API.md                               # API 端点清单
PROJECT_PLAN.md                      # 项目规划、进度与调研数据
调研报告摘要-立项书版.docx           # 立项书版调研摘要
大学生成长路径与竞赛参与需求调研-默认报告.pdf # 问卷原始数据
```

### 外部参考

参考项目位于 `/Users/liuzy/Desktop/mess/雏雁计划/RepositoryDemo/`：
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
| LLM 兼容层 | OkHttp + Gson (OpenAI 兼容协议) | 抽象出 `LlmClient`，支持 tools/SSE；`/api/chat/*` 禁用 fallback，其余用途保留可配置 fallback |
| LLM 配置 | 按用途分组（qa/planning/profile/sql/chat） | 8 月模型对比实验时灵活替换 |

---

## 关键命令

```bash
# Java 编译检查
./mvnw compile

# 数据导入（独立 CLI，不启动 Web）
./import.sh --import-dir=../样例 --report=target/import-report.json
./import.sh --import-dir=../样例 --dry-run

# 测试（164 个）
./mvnw test -Dspring.profiles.active=test
# 若沙箱/权限环境报 sqlite 原生库解包失败，加：-Djava.io.tmpdir=target/tmp（先 mkdir -p target/tmp）

# 启动验证（本地 mock 模式）
./run.sh

# 测试 API
curl -s http://localhost:18000/api/users/current
curl -s http://localhost:18000/api/qa/health

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
- 迁移逻辑在各 Repository 的 `migrateUserSchema()` 中逐列 `PRAGMA table_info` 检测并 ALTER TABLE（users、user_profiles）；`DatabaseInitializer.migrateLegacyGoals()` 负责把存量 goals 表行一次性合并进 `user_profiles.goals` 后 DROP 旧表（幂等，表不存在即跳过）

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
- 历史：`/api/planning/*`
- 问答：`/api/qa/*`
- 对话：`/api/chat/*`
- 加分规则：`/api/college-credit-rules/*`
- 加分记录：`/api/credit-activities/*`
- 成长资源：`/api/resources/*`
- RAG 切片：`/api/rag-chunks/*`
- 能力标签/字典：`/api/capability-tags/*`、`/api/capability-reference/*`
- 页面路由：通过 `HomeController` 的 `@Controller` 映射

### Security 配置
- 认证方式：JSESSIONID Cookie（Session-based）
- **CSRF（v0.0.5）**：启用 Spring Security CSRF；`XSRF-TOKEN` Cookie + `X-XSRF-TOKEN` 头；前端统一加载 `/csrf.js`；状态变更请求无 token/错 token 返回 403
- **Session Cookie**：`HttpOnly=true`、`SameSite=Lax`、HTTPS 时 `Secure`；登录成功重建 Session
- CORS：默认本地开发环境（`http://localhost:*`, `http://127.0.0.1:*`, `https://localhost:*`），可通过 `app.security.allowed-origins` 显式配置
- 详细端点权限（公开/ADMIN/已登录）：详见 [`API.md`](./API.md) 的「认证与权限说明」章节
- 公开端点：`/api/csrf`, `/api/users/register`, `/api/users/login`, `/api/feedback/submit`, `/api/feedback/system-error`, `/api/qa/health`
- 需要 ADMIN 角色：反馈管理、用户管理、封禁操作、POST `/api/college-credit-rules`（控制器内校验 ROLE_ADMIN）
- 需要认证：`/api/users/current`, `/api/users/logout`, `/api/profiles/**`, `/api/planning/**`, `/api/qa/ask`, `/api/chat/**`, `/api/college-credit-rules/**`, `/api/credit-activities/**`, `/api/resources/**`, `/api/rag-chunks/**`, `/api/capability-tags/**`, `/api/capability-reference/**`
- JSESSIONID Cookie 传递认证状态
- CORS 已限制为本地开发环境（`http://localhost:*`, `http://127.0.0.1:*`, `https://localhost:*`）

### LLM 配置层
- 按用途分组，每组字段：`provider`, `baseUrl`, `model`, `apiKeyEnv`, `enabled`, `timeoutMillis`, `temperature`
- 默认全部指向 DeepSeek `https://api.deepseek.com/v1`，模型 `deepseek-flash`，`enabled=false`（`run.sh` 默认开启 qa/chat）
- `LlmClientFactory`：根据 `useCase` 名称路由到对应客户端
- `FallbackLlmClient`：仅 `enabled=false` 时兜底，返回 mock 响应；**`/api/chat/*` 显式拒绝 fallback**
- `OpenAiCompatibleLlmClient`：OkHttp 适配器，支持 tools 定义、tool_calls 解析和 SSE 流式响应
- `OllamaEmbeddingClient`：独立 embedding 客户端，固定 `POST /v1/embeddings`，不读取/打印 API key
- API Key 读取方式：从 `apiKeyEnv` 指定的环境变量读取，代码和日志中**永不出现**真实 key

### 测试基础设施
- 测试基类：`BaseControllerTest`（MockMvc + SecurityMockMvc + 独立测试 DB）
- 隔离数据库：`target/test-data.db`（通过 `application-test.properties` 配置）
- **生产数据永不污染**：测试不触碰 `data.db`，启动时通过 `before/after` 时间戳验证
- 全量测试 188 个，通过 `./mvnw test -Dspring.profiles.active=test` 一键运行

### Run Script (`run.sh`)
- 所有 LLM 配置通过环境变量注入：`LLM_QA_PROVIDER`, `LLM_QA_BASE_URL`, `LLM_QA_MODEL`, `LLM_QA_API_KEY_ENV`, `LLM_QA_ENABLED`（qa/planning/profile/sql/chat 五个用途重复此模式）
- 默认值：`provider=deepseek`, `baseUrl=https://api.deepseek.com/v1`, `model=deepseek-flash`；`qa` / `chat` 默认 `enabled=true`，其余默认 `false`
- 切换到真实 LLM：在 `.env` 或 shell 中提供 `DEEPSEEK_API_KEY`；其他 provider 可覆盖对应 `LLM_*_*` 变量

---

## 开发风格约定

1. **构造函数注入**（非 `@Autowired` 字段注入）
2. **Service 抛出 `IllegalStateException`** 用于业务错误，Controller 捕获后返回 400
3. **Repository 返回 `boolean` / `int`** 表示操作结果，不抛运行时异常
4. **DTO 使用 Lombok**（`@Getter @Setter`），Entity 同理
5. **`@Valid` 校验**在 Controller 参数上，`RegisterRequest`/`LoginRequest` 已配置 `@NotBlank` + `@Size`
6. **Fix 注释风格**：`// FIX: explanation` 记录 AI 识别并修复的老代码问题
7. **日志使用 Log4j2**（Spring Boot 默认 Logback 已被排除）
8. **所有权检查**：任何访问用户私有数据（profile/credit-activities/planning/chat）的端点**必须**通过 SecurityContext 解析当前用户并验证归属，禁止跨用户读取

---

## 数据库与 API 参考

> - 数据库表结构：详见 [`SCHEMA.md`](./SCHEMA.md)
> - API 端点清单：详见 [`API.md`](./API.md)

## 立项信息

> 详见 [`PROJECT_PLAN.md`](./PROJECT_PLAN.md)（项目规划、进度与调研数据）

### 额外信息

1. 在每次开发结束之后，当我指出需要更新release note更新日志时，请根据之前的开发内容，更新[release_notes.html](LifeComposer/external/templates/release_notes.html)(如果没有则创建文件并撰写)
   - 示例文件：[release_notes_example.html](LifeComposer/external/templates/release_notes_example.html)
   - 要求格式尽量统一。如果需要新的样式，可以添加，但需要在更新后向我汇报
   - 维护约定：在新版本现有版本容器之前插入新的更新日志，每个新版本是一个 <div class="container"> 容器块。顺序：新版本在顶部，旧版本依次往下（最新在最上） 
   - 同时在AGENTS.md（本文件）最后添加相应历史版本信息
2. 现在可以使用的LLM api有：deepseek、qwen（百炼模型）、glm、gpt（Longxia api）、ollama本地模型（qwen 3.5:9b），api key在环境变量中，请不要阅读，需要api key时指出填写位置，由我手动填写

---

## 安全约束

> 以下约束不可违反。

- **永不把 API key 写进代码、配置文件、测试、日志或文档**
- **永不提交 `data.db` 到 Git**
- **永不在 `run.sh` 以外读取或打印 API key**
- **永不读取或泄露 `/Users/liuzy/.config/opencode/opencode.jsonc` 和 `/Users/liuzy/.zshrc` 中的密钥值**

---

## 历史版本

| 版本 | 日期 | 主要内容 |
|------|------|----------|
| v0.0.1 | 2026-06-27 | 初始后端服务器基础（用户/反馈/画像/目标/历史/问答/LLM 抽象） |
| v0.0.2 | 2026-06-28 | AI 对话原型（chat_messages + /api/chat）+ Log4j2 日志系统 + 缺陷修复 |
| v0.0.3 | 2026-09-05 | 移除独立 goals 表并入画像；新增成长数据底座 6 表（加分规则/记录、资源、RAG、能力字典）与基础 API；文档同步 |
| v0.0.4 | 2026-09-13 | 独立数据导入 CLI；Ollama embedding + SQLite RAG 检索；Agent 多轮 tool-use；`/api/chat/stream` SSE 与 chat_test.html 过程展示；生成用途切 deepseek-flash，`/api/chat/*` 禁止 fallback；164 测试全绿 |
| v0.0.5 | 2026-09-13 | Milestone 5 安全加固：CSRF、Session 加固、聊天分钟/每日限流与 chat_usage_daily、max_tokens=1024、管理员重置当日额度、注册/登录限速与审计日志；188 测试全绿 |
