# API 端点清单

> v0.0.3 当前已实现端点（2026-09-05 同步）。与各 Controller 及 WebSecurityConfig 保持一致。

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
| POST | `/api/chat/send` | 已登录 | 发送消息，返回 ChatResponse：`role`(assistant)、`content`、`createTime`、`mocked`（是否降级 mock）、`error` |
| GET | `/api/chat/history` | 已登录 | 获取当前用户的对话历史（`chat_messages` 按时间稳定排序） |
| DELETE | `/api/chat/context` | 已登录 | 清除当前用户的对话上下文，返回 `{"deleted": n}` |

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

## 认证与权限说明

- **公开端点**：`/api/users/register`, `/api/users/login`, `/api/feedback/submit`, `/api/feedback/system-error`, `/api/qa/health`
- **需要 ADMIN 角色**：`/api/users/all`, `/api/users/*/grant-admin`, `/api/users/*/revoke-admin`, `/api/users/*/ban`, `/api/users/*/unban`, `/api/users/admin/**`, `/api/feedback/all`, `/api/feedback/type/**`, `/api/feedback/*/resolve`, `POST /api/college-credit-rules`
- **需要认证**：`/api/users/current`, `/api/users/logout`, `/api/profiles/**`, `/api/planning/**`, `/api/qa/ask`, `/api/chat/**`, `/api/college-credit-rules/**`（除 POST 管理外）, `/api/credit-activities/**`, `/api/resources/**`, `/api/rag-chunks/**`, `/api/capability-tags/**`, `/api/capability-reference/**`, `/front/**`
- **v0.0.3 变更**：原目标管理端点相关认证匹配已随 goals 表一并移除；新增 6 个 `/api` 前缀的认证配置（college-credit-rules / credit-activities / resources / rag-chunks / capability-tags / capability-reference）
- **认证机制**：JSESSIONID Cookie 传递认证状态
- **CORS 限制**：本地开发环境（`http://localhost:*`, `http://127.0.0.1:*`, `https://localhost:*`）
