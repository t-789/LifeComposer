# API 端点清单

> v0.0.5 当前已实现端点（2026-09-13 同步）。与各 Controller 及 WebSecurityConfig 保持一致。Milestone 5 起所有状态变更请求需要 CSRF token。

---

## 用户管理

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/users/register` | 无 | 注册（用户名 3-20 字符，密码 1-50） |
| POST | `/api/users/login` | 无 | 登录，设置 session |
| POST | `/api/users/logout` | 已登录 | 登出，清除 session |
| GET | `/api/users/current` | 已登录 | 获取当前用户信息（id/username/type/avatar/isBanned/banEndTime） |
| GET | `/api/users/all` | ADMIN | 获取所有用户（不含密码哈希） |
| PUT | `/api/users/{userId}/grant-admin` | ADMIN | 授予管理员 |
| PUT | `/api/users/{userId}/revoke-admin` | ADMIN | 撤销管理员 |
| PUT | `/api/users/{userId}/ban` | ADMIN | 封禁（时间格式：`1d`, `30m`, `1y`，`0` 为永久） |
| PUT | `/api/users/{userId}/unban` | ADMIN | 解封 |
| POST | `/api/users/admin/reset-password/{userId}` | ADMIN | 重置密码为 000000 |
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

## 页面路由

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 首页 |
| GET | `/login` | 登录页 |
| GET | `/admin` | 管理后台 (ADMIN) |
| GET | `/admin/user` | 用户管理页 (ADMIN) |
| GET | `/admin/feedback_management` | 反馈管理页 (ADMIN) |
| GET | `/release-notes` | 发布说明 |
| GET | `/front/chat_test` | AI 对话测试 |

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
- **需要 ADMIN 角色**：`/api/users/all`, `/api/users/*/grant-admin`, `/api/users/*/revoke-admin`, `/api/users/*/ban`, `/api/users/*/unban`, `/api/users/admin/**`, `/api/feedback/all`, `/api/feedback/type/**`, `/api/feedback/*/resolve`, `POST /api/college-credit-rules`
- **需要认证**：`/api/users/current`, `/api/users/logout`, `/api/profiles/**`, `/api/planning/**`, `/api/qa/ask`, `/api/chat/**`, `/api/college-credit-rules/**`（除 POST 管理外）, `/api/credit-activities/**`, `/api/resources/**`, `/api/rag-chunks/**`, `/api/capability-tags/**`, `/api/capability-reference/**`, `/front/**`
- **v0.0.3 变更**：原目标管理端点相关认证匹配已随 goals 表一并移除；新增 6 个 `/api` 前缀的认证配置（college-credit-rules / credit-activities / resources / rag-chunks / capability-tags / capability-reference）
- **认证机制**：JSESSIONID Cookie 传递认证状态
- **CORS 限制**：默认本地开发环境（`http://localhost:*`, `http://127.0.0.1:*`, `https://localhost:*`），可通过 `app.security.allowed-origins` 显式覆盖
- **限流与额度**：注册（每 IP/分钟）、登录（连续失败临时锁定）、聊天（每用户 5/分钟 + 100/天），详见对应章节
