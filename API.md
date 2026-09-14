# API 端点清单

> v0.0.6 当前已实现端点（2026-09-14 同步，含审查整改）。与各 Controller 及 WebSecurityConfig 保持一致。Milestone 5 起所有状态变更请求需要 CSRF token；Milestone 7 起 `/api/admin/**` 与 `/admin/**` 全部仅限 `ROLE_ADMIN`。

---

## 用户管理

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/users/register` | 无 | 注册（用户名 3-20 字符，密码 1-50） |
| POST | `/api/users/login` | 无 | 登录并设置 session。返回 JSON：`message` / `username` / `type` / `passwordChangeRequired` / `tempPasswordExpiresAt`。密码错误返回 400 `BAD_CREDENTIALS`；临时密码过期返回 403 `TEMP_PASSWORD_EXPIRED`；`passwordChangeRequired=true` 时必须先访问 `/front/change-password` |
| POST | `/api/users/logout` | 已登录 | 登出，清除 session |
| GET | `/api/users/current` | 已登录 | 获取当前用户信息（id/username/type/avatar/isBanned/banEndTime） |
| GET | `/api/users/all` | ADMIN | 获取所有用户（不含密码哈希） |
| PUT | `/api/users/{userId}/grant-admin` | ADMIN | 授予管理员 |
| PUT | `/api/users/{userId}/revoke-admin` | ADMIN | 撤销管理员；系统必须保留至少一个可登录管理员，否则返回 409 `LAST_ADMIN_PROTECTED`；用户不存在返回 404 |
| PUT | `/api/users/{userId}/ban` | ADMIN | 封禁（body `{"banTime":"1d|30m|1y|0"}`，`0` 为永久；body 缺失/非法返回 400，最后一个可登录管理员返回 409 `LAST_ADMIN_PROTECTED`） |
| PUT | `/api/users/{userId}/unban` | ADMIN | 解封 |
| POST | `/api/users/admin/reset-password/{userId}` | ADMIN | v0.0.6：下发**临时密码**，body `{"newPassword":"..."}`。管理员账户要求 ≥12 字符、普通用户账户要求 ≥8 字符，且均不得是已知弱密码；响应只返回 `message`/`passwordChangeRequired`/`tempPasswordExpiresAt`，绝不回显密码。临时密码带状态与有效期，见下文「临时密码生命周期」 |
| POST | `/api/users/password` | 已登录 | v0.0.6：自助修改密码，body `{"currentPassword":"...","newPassword":"..."}`；错误当前密码/弱新密码/与原密码相同返回 400 `PASSWORD_REJECTED`。成功后当前会话保留，该账户的其他会话立即失效 |
| GET | `/front/change-password` | 已登录 | v0.0.6：修改密码页面（临时密码会话唯一可访问的业务页面） |
| POST | `/api/users/admin/chat-quota/reset/{userId}` | ADMIN | v0.0.5：重置指定用户当前 Asia/Shanghai 自然日的聊天额度，返回新的剩余额度 |
| GET | `/api/csrf` | 无 | v0.0.5：返回当前会话的 CSRF token（headerName/token），并写入可读的 XSRF-TOKEN Cookie |

## 反馈管理

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/feedback/submit` | 无 | 用户反馈（可匿名，已登录则记录用户） |
| POST | `/api/feedback/system-error` | 无 | 系统错误报告 |
| GET | `/api/feedback/all` | ADMIN | 所有反馈 |
| GET | `/api/feedback/type/{type}` | ADMIN | 按类型过滤 |
| POST | `/api/feedback/{id}/resolve` | ADMIN | 标记已解决/未解决（body `resolved` 缺省视为 true） |

## 用户画像

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/profiles/me` | 已登录 | 获取当前用户画像 |
| PUT | `/api/profiles/me` | 已登录 | Upsert 当前用户画像 |

- 画像字段：`college`、`major`、`grade`、`studentId`、`skillsJson`、`interestsJson`、`experiencesJson`、`preferencesJson`
- **v0.0.3 新增字段**：`availableTime`（每周可投入时间，自由文本）、`goals`（用户成长目标，JSON 数组字符串）
- 原独立 `goals` 表及其整套目标 CRUD 端点已移除，目标统一由 `user_profiles.goals` 列承载

## 规划历史

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/planning/history` | 已登录 | 当前用户的 AI 交互历史 |
| GET | `/api/planning/history/{id}` | 已登录 | 历史详情（仅本人） |

## 问答

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/qa/health` | 无 | LLM 健康检查（返回 provider/model/enabled/status 状态） |
| POST | `/api/qa/ask` | 已登录 | 提问，返回回答和 historyId |

## 对话 (Chat)

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/chat/send` | 已登录 | 兼容旧客户端的一次性 JSON；内部复用同一 Agent 多轮 tool-use 逻辑。LLM 不可用时返回 HTTP 503 `{"error":"LLM_UNAVAILABLE",...}`；超过分钟/日额度返回 HTTP 429，body 含 retryAfterSeconds / remainingToday |
| POST | `/api/chat/stream` | 已登录 | SSE 流式对话。事件：`thinking_start` / `thinking_tick`(可选) / `thinking_end` / `tool_call` / `tool_result` / `token` / `assistant_message` / `error` / `done`；准入失败返回 HTTP 429 JSON 而不是 SSE |
| GET | `/api/chat/history` | 已登录 | 获取当前用户的对话历史（`chat_messages` 按时间稳定排序）；包含 user / assistant / tool 行 |
| DELETE | `/api/chat/context` | 已登录 | 清除当前用户的对话上下文，返回 `{"deleted": n}` |

> **v0.0.4 说明**：`/api/chat/*` 不使用 fallback 或 mock 回复。API key 缺失、HTTP 非 2xx、超时、格式错误、流中断均视为 `LLM_UNAVAILABLE`；SSE 发送 `error` 事件后关闭。一次工具调用会持久化两条 `chat_messages`：assistant 的 tool_call JSON 和 tool 的 tool_result JSON。
>
> - 思考计时：首次收到 `reasoning_content` 才发送 `thinking_start`；reasoning 中出现 `</think>` / `done thinking`，或 reasoning 阶段结束（首个 `content`、流结束、报错）时发送 `thinking_end`。普通 `token` / `tool_call` 不再提前停止计时。
> - 持久化顺序：SSE 场景下先成功发送 `tool_call` / `tool_result` / `assistant_message` 事件，再写入数据库；`tool_call` 与对应 `tool_result` 成对原子写入，浏览器断开时不会留下未送达或不成对的 tool/assistant 行。非流式 `/api/chat/send` 直接写入。
> - 多工具与轮次边界：同一轮返回多个工具调用时，写入数据库仍按每次调用两条记录，并在 tool_call/tool_result JSON 中携带 `turnId`；拼装下一轮 LLM 上下文时按 `turnId` 分组，同一轮合并为一条带多个 `tool_calls` 的 assistant 消息 + 多条 tool 结果，不同轮次保持独立，符合 OpenAI/DeepSeek 协议。旧数据无 `turnId` 时回退为连续记录分组。
> - thinking 计时状态、未完成 assistant 文本不落库；连接中断时保留已完整写入的 user 消息和已成功送达的工具调用记录。
>
> **v0.0.5 配额说明**：
> - 每个已认证用户每分钟最多 5 次聊天，每天（Asia/Shanghai）最多 100 次；`/api/chat/send` 与 `/api/chat/stream` 每次请求只计一次。
> - 被 429 拒绝的请求不扣减日额度；LLM 失败、SSE 中断和客户端断开仍保留已准入请求的用量，避免通过失败绕过限额。
> - 服务端强制 `max_tokens=1024`；客户端传入更大的 `maxTokens` 会被截断。
> - 管理员可用 `POST /api/users/admin/chat-quota/reset/{userId}` 重置当前自然日额度，并写入审计日志。

## 加分规则 (College Credit Rules)

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/college-credit-rules` | 已登录 | 规则列表；可选过滤参数 `college`、`creditType`(graduation/recommendation)、`category` |
| GET | `/api/college-credit-rules/{id}` | 已登录 | 规则详情 |
| POST | `/api/college-credit-rules` | ADMIN | 新建规则（校验 college/creditType/category/credits） |

## 加分记录 (Credit Activities)

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/credit-activities` | 已登录 | 当前用户的加分记录列表 |
| GET | `/api/credit-activities/{id}` | 已登录 | 加分记录详情（仅本人） |
| POST | `/api/credit-activities` | 已登录 | 新建加分记录（归属当前用户） |
| PUT | `/api/credit-activities/{id}` | 已登录 | 更新加分记录（仅本人；`verified` 由服务端控制） |
| DELETE | `/api/credit-activities/{id}` | 已登录 | 删除加分记录（仅本人） |

## 成长资源库 (Resources)

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/resources` | 已登录 | 资源列表；可选 `type` 过滤（`competition` 或 `course`），不带参数返回全部 |
| GET | `/api/resources/{id}` | 已登录 | 资源详情：优先按业务键 `resource_id`（如 `competition_001`）匹配，纯数字回退物理主键 |

## RAG 检索切片 (RAG Chunks)

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/rag-chunks` | 已登录 | 切片列表；可选 `relatedResourceId` 过滤（关联 `resources.resource_id` 业务键），不带参数返回全部 |

## 能力标签字典 (Capability Tags)

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/capability-tags` | 已登录 | 全部标准能力标签（按标签名排序） |

## 能力映射字典 (Capability Reference)

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/capability-reference` | 已登录 | 字典行列表；`section` 必填时仅支持：`tags_to_merge`、`skill_mapping`、`skill_profiles`、`role_profiles`、`major_categories`、`_meta`；不带参数返回全部 |

## 管理控制台 (Admin Console, v0.0.6)

> 全部为只读 `GET`，仅 `ROLE_ADMIN` 可访问；不提供任意 SQL、任意表名/列名、数据库文件下载或批量导出。
> 统一分页信封：`{"items":[...],"page":1,"pageSize":50,"total":123,"totalPages":3}`；默认每页 50，最大 200。
> 统一错误：`{"error":"<CODE>","message":"..."}`，错误码包括 `INVALID_PARAMETER`、`UNKNOWN_PARAMETER`、`NOT_FOUND`、`ADMIN_RATE_LIMIT`、`ADMIN_QUERY_FAILED`、`FORBIDDEN`。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/dashboard` | 总览：12 张表记录数、用户/管理员/封禁/画像统计、今日聊天用量与 TOP 用户、未解决反馈、LLM 失败数、RAG embedding 状态分布 |
| GET | `/api/admin/users` | 用户与额度列表：状态、角色、创建时间、是否有画像、当日已用/剩余额度；筛选 `q`/`type`(1,2)/`banned`(0,1)/`hasProfile`(0,1) |
| GET | `/api/admin/users/{id}/profile` | 单个用户画像详情（含受控展示的 `preferencesJson`、完整学号）；不存在返回 404 |
| GET | `/api/admin/user-profiles` | 画像列表：学院/专业/年级/技能/兴趣/经历/目标/可用时间；`studentId` 默认掩码，`preferencesJson` 不入列表；筛选 `userId`/`q`/`college`/`major`/`grade` |
| GET | `/api/admin/planning-history` | `planning_history` 列表（仅摘要 + 长度）；筛选 `userId`/`type`/`status`/`provider`/`model`/`dateFrom`/`dateTo` |
| GET | `/api/admin/planning-history/{id}` | 单条详情，请求/响应 JSON 截断后受控展示 |
| GET | `/api/admin/chat-messages` | `chat_messages` 列表（摘要 + `structured` 标记）；筛选 `userId`/`role`(user,assistant,tool)/`dateFrom`/`dateTo` |
| GET | `/api/admin/chat-messages/{id}` | 单条完整内容（tool call/result 结构）；超长截断并返回 `contentTruncated` |
| GET | `/api/admin/feedback` | 反馈与系统错误列表；`user_agent` 截断、不返回 `stack_trace`；筛选 `type`(user,system)/`resolved`(0,1)/`userId`/日期 |
| GET | `/api/admin/feedback/{id}` | 单条详情：URL、UA、受控截断的堆栈（不超过 `console-diagnostic-length`） |
| GET | `/api/admin/college-credit-rules` | 加分规则全字段分页；筛选 `college`/`creditType`/`category`/`compLevel`/`awardTier`/`q` |
| GET | `/api/admin/college-credit-rules/{id}` | 规则详情（含完整备注） |
| GET | `/api/admin/credit-activities` | 加分记录；`certificate_ref` 不返回，只给 `hasCertificate`；筛选 `userId`/`creditType`/`verified` |
| GET | `/api/admin/credit-activities/{id}` | 记录详情 |
| GET | `/api/admin/resources` | 资源库；筛选 `type`/`difficulty`/`dataQuality`/`provider`/`q` |
| GET | `/api/admin/resources/{id}` | 资源详情（JSON 字段安全格式化展示） |
| GET | `/api/admin/rag-chunks` | RAG 切片；不返回 `embedding_json`，只给 `embeddingJsonLength`、模型、维度、状态、hash、错误摘要；筛选 `embeddingStatus`/`sourceType`/`relatedResourceId`/`q` |
| GET | `/api/admin/rag-chunks/{chunkId}` | 切片详情（正文截断后展示） |
| GET | `/api/admin/capability-tags` | 能力标签字典；筛选 `category`/`q` |
| GET | `/api/admin/capability-tags/{name}` | 标签详情（别名/证据 JSON 安全格式化） |
| GET | `/api/admin/capability-reference` | 能力引用字典；筛选 `section`/`q` |
| GET | `/api/admin/capability-reference/{id}` | 字典条目详情 |
| GET | `/api/admin/chat-usage` | `chat_usage_daily` 每日计数；筛选 `userId`/`usageDate`/`dateFrom`/`dateTo` |

**通用参数**：`page`（≥1）、`pageSize`（1-200）、`sort`（每个数据集独立白名单）、`dir`（`asc`/`desc`）；未在数据集白名单内的参数一律 400 `UNKNOWN_PARAMETER`。
**限流**：每个管理员每分钟最多 `lifecomposer.admin.console-queries-per-minute`（默认 240）次查询，超限 429 `ADMIN_RATE_LIMIT`；查询使用 `lifecomposer.admin.console-query-timeout-seconds`（默认 10s）JDBC statement timeout。
**审计**：成功查询记录 `AUDIT event=admin_query`（admin/dataset/filters/total/returned/elapsedMs），被拒查询记录 `AUDIT event=admin_query_rejected`（admin/uri/reason）；两者都不记录被查看的正文内容。

## 管理员初始化与密码策略（v0.0.6）

- 首次启动（数据库中没有任何管理员）必须通过环境变量 `LIFECOMPOSER_INITIAL_ADMIN_PASSWORD` 提供初始管理员密码；缺失、空白或不满足强度要求时应用 fail-fast，启动失败信息只包含变量名。
- 管理员密码强度：≥ `lifecomposer.admin.min-password-length`（默认 12）字符、≤ 72 字符、不含空白字符，且不能是 `admin`/`000000`/`testuser` 等已知弱密码或同一字符重复。
- 数据库中已存在管理员时忽略该变量，且绝不覆盖现有密码；检测到历史遗留的 `admin/admin` 弱密码账户时必须提供合格新密码才能启动，启动后立即轮换并记录 `AUDIT event=admin_password_rotated`。
- 独立导入 CLI（`lifecomposer.import.mode=true`）跳过管理员初始化，不要求该变量。
- 哈希只以 BCrypt 形式存储；密码值不出现在日志、异常、响应或报告中。

### 临时密码生命周期（v0.0.6 审查整改）

管理员下发的密码是**真正的临时密码**，不再等同于一次普通重置：

| 语义 | 实现 |
|------|------|
| 状态 | `users.password_reset_required = 1`；登录响应返回 `passwordChangeRequired: true` |
| 有效期 | `users.temp_password_expires_at`，由 `lifecomposer.admin.temp-password-ttl-minutes`（默认 1440 分钟）决定；过期后登录返回 403 `TEMP_PASSWORD_EXPIRED`，强制修改期间过期同样会立即登出 |
| 首次登录强制修改 | 带该状态的服务端会话只能访问 `POST /api/users/password`、`GET /api/users/current`、`POST /api/users/logout`、`GET /api/csrf` 与 `/front/change-password`；其他请求返回 401 `PASSWORD_CHANGE_REQUIRED`（页面重定向到 `/front/change-password`） |
| 一次性 | 修改成功后 `password_reset_required = 0`、`temp_password_expires_at = NULL`，旧临时密码立即失效 |
| 会话失效 | 任何密码变更都会 `credential_version = credential_version + 1`；会话登录时记录该版本号，后续请求版本不匹配即被服务端销毁并返回 401 `SESSION_EXPIRED`（发起修改的会话会被重新盖章，因此本人不被登出，其他设备全部失效） |

> 实现：`Security/SessionCredential` + `Security/SessionCredentialGuardFilter`（挂在 Spring Security 过滤器链的 `AuthorizationFilter` 之前）+ `UserService.setTemporaryPassword/changeOwnPassword`。
>
> 密码本身仍只以 BCrypt 哈希存储；`temp_password_expires_at` 的值绝不会回显。

### 并发安全的管理员保护（v0.0.6 审查整改）

「系统必须保留至少一个可登录管理员」不再依赖“先查数量再更新”：

- `UserRepository.revokeAdminIfNotLast()` / `banIfNotLastLoginableAdmin()` 把数量条件写进 `UPDATE ... WHERE` 子句，且只作用于单行；
- 两个操作都在 `@Transactional` 中执行，连接串使用 `transaction_mode=immediate`，事务开始即取得 SQLite 写锁，因此并发调用被串行化，不可能同时通过检查；
- 回归测试 `AdminLastAdminConcurrencyTest` 用栅栏同时释放多个线程，验证只有 `n-1` 次能成功、最终至少保留 1 个可登录管理员。

## 管理控制台信任模型（v0.0.6 审查确认）

管理员被定义为**完全可信的调试/运维角色**（单实例部署）：

- **绝对不返回**：`password_hash`、原始 `embedding_json` 向量、`certificate_ref` 证书路径、服务端环境变量与会话/Cookie；
- **可以看到**：用户画像中的个人信息。画像列表为了减少肩窥风险把学号做掩码显示，而 `GET /api/admin/users/{id}/profile` 详情会返回**完整学号**与 `preferencesJson`，因为排查用户规划结果时需要这些字段。

同一表述同时写在 `AdminController` 的类注释与 `external/static/admin.js` 的文件头，修改信任模型时必须三处同步。

## 页面路由

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 首页 |
| GET | `/login` | 登录页 |
| GET | `/admin` | 运维控制台 · 总览 (ADMIN) |
| GET | `/admin/user` | 运维控制台 · 用户与额度 (ADMIN) |
| GET | `/admin/profile` | 运维控制台 · 用户画像 (ADMIN) |
| GET | `/admin/planning` | 运维控制台 · AI 规划记录 (ADMIN) |
| GET | `/admin/chat` | 运维控制台 · AI 对话记录 (ADMIN) |
| GET | `/admin/feedback_management` | 运维控制台 · 反馈与错误 (ADMIN) |
| GET | `/admin/credit-rules` | 运维控制台 · 加分规则 (ADMIN) |
| GET | `/admin/credit-activities` | 运维控制台 · 加分记录 (ADMIN) |
| GET | `/admin/resources` | 运维控制台 · 资源库 (ADMIN) |
| GET | `/admin/rag` | 运维控制台 · RAG 切片 (ADMIN) |
| GET | `/admin/capability-tags` | 运维控制台 · 能力标签 (ADMIN) |
| GET | `/admin/capability-reference` | 运维控制台 · 能力字典 (ADMIN) |
| GET | `/admin/usage` | 运维控制台 · 聊天用量 (ADMIN) |
| GET | `/release-notes` | 发布说明 |
| GET | `/front/chat_test` | AI 对话测试 |
| GET | `/front/change-password` | 修改密码页（v0.0.6，临时密码会话唯一可访问的业务页面） |

> v0.0.6：`/admin` 与 `/admin/**` 在 Spring Security 过滤链上即要求 `ROLE_ADMIN`，页面路由内还有一层 `isAdmin` 校验；全部页面共用 `external/static/admin.css` + `external/static/admin.js`（同一套导航、分页器、筛选栏与详情抽屉），页面模板只声明 `data-admin-view`。

---

## CSRF 防护（v0.0.5）

- 服务端使用 `CookieCsrfTokenRepository`，通过可读的 `XSRF-TOKEN` Cookie + `GET /api/csrf` 暴露 token。
- 前端统一加载 `/csrf.js`，包装 `window.fetch`，对 `POST` / `PUT` / `DELETE` 自动发送 `X-XSRF-TOKEN` 头。
- `GET` / `HEAD` / `OPTIONS` 不需要 CSRF token；其余状态变更请求缺少或携带不匹配 token 时返回 403。
- Session Cookie 设置 `HttpOnly=true`、`SameSite=Lax`；HTTPS 部署时设置 `SESSION_COOKIE_SECURE=true` / `CSRF_COOKIE_SECURE=true`。`XSRF-TOKEN` 需要被同源 JS 读取，因此不设为 HttpOnly。
- CORS 允许来源通过 `app.security.allowed-origins` 显式配置，禁止使用 `*` 搭配 credentials。
- 登录成功后重建 Session，防止 Session Fixation。

## 认证与权限说明

- **公开端点**：`/api/users/register`, `/api/users/login`, `/api/csrf`, `/api/feedback/submit`, `/api/feedback/system-error`, `/api/qa/health`
- **需要 ADMIN 角色**：`/api/admin/**`（v0.0.6 管理控制台）, `/admin` 与 `/admin/**`（v0.0.6 管理页面）, `/api/users/all`, `/api/users/*/grant-admin`, `/api/users/*/revoke-admin`, `/api/users/*/ban`, `/api/users/*/unban`, `/api/users/admin/**`, `/api/feedback/all`, `/api/feedback/type/**`, `/api/feedback/*/resolve`, `POST /api/college-credit-rules`
- **v0.0.6 变更**：`POST /api/users/admin/reset-password/{userId}` 下发带状态与有效期的临时密码且不回显，用户首次登录必须通过 `POST /api/users/password` 修改；撤销或封禁最后一个可登录管理员返回 409，保证系统始终存在可登录管理员
- **v0.0.6 会话失效**：密码变更会递增 `users.credential_version`，服务端会立即销毁用旧密码建立的会话（401 `SESSION_EXPIRED`）；带临时密码状态的会话只能访问修改密码相关端点（401 `PASSWORD_CHANGE_REQUIRED`）
- **需要认证**：`/api/users/password`（v0.0.6 自助修改密码）, `/api/users/current`, `/api/users/logout`, `/api/profiles/**`, `/api/planning/**`, `/api/qa/ask`, `/api/chat/**`, `/api/college-credit-rules/**`（除 POST 管理外）, `/api/credit-activities/**`, `/api/resources/**`, `/api/rag-chunks/**`, `/api/capability-tags/**`, `/api/capability-reference/**`, `/front/**`
- **v0.0.3 变更**：原目标管理端点相关认证匹配已随 goals 表一并移除；新增 6 个 `/api` 前缀的认证配置（college-credit-rules / credit-activities / resources / rag-chunks / capability-tags / capability-reference）
- **认证机制**：JSESSIONID Cookie 传递认证状态
- **CORS 限制**：默认本地开发环境（`http://localhost:*`, `http://127.0.0.1:*`, `https://localhost:*`），可通过 `app.security.allowed-origins` 显式覆盖
- **限流与额度**：注册（每 IP/分钟）、登录（连续失败临时锁定）、聊天（每用户 5/分钟 + 100/天），详见对应章节
