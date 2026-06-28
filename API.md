# API 端点清单

> v0.0.1 已实现端点。8月主体开发将继续扩展。

---

## 用户管理

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/users/register` | 无 | 注册（用户名 3-20 字符，密码 1-50） |
| POST | `/api/users/login` | 无 | 登录，设置 session |
| POST | `/api/users/logout` | 已登录 | 登出，清除 session |
| GET | `/api/users/current` | 已登录 | 获取当前用户信息 |
| GET | `/api/users/all` | ADMIN | 获取所有用户 |
| PUT | `/api/users/{id}/grant-admin` | ADMIN | 授予管理员 |
| PUT | `/api/users/{id}/revoke-admin` | ADMIN | 撤销管理员 |
| PUT | `/api/users/{id}/ban` | ADMIN | 封禁（时间格式：`1d`, `30m`, `1y`，`0` 为永久）|
| PUT | `/api/users/{id}/unban` | ADMIN | 解封 |
| POST | `/api/users/admin/reset-password/{id}` | ADMIN | 重置密码为 000000 |

## 反馈管理

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/feedback/submit` | 无 | 用户反馈 |
| POST | `/api/feedback/system-error` | 无 | 系统错误报告 |
| GET | `/api/feedback/all` | ADMIN | 所有反馈 |
| GET | `/api/feedback/type/{type}` | ADMIN | 按类型过滤 |
| POST | `/api/feedback/{id}/resolve` | ADMIN | 标记已解决 |

## 用户画像

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/profiles/me` | 已登录 | 获取当前用户画像 |
| PUT | `/api/profiles/me` | 已登录 | Upsert 当前用户画像 |

## 目标管理

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/goals` | 已登录 | 创建目标 |
| GET | `/api/goals` | 已登录 | 列出当前用户的目标 |
| GET | `/api/goals/{id}` | 已登录 | 目标详情（仅本人） |
| PUT | `/api/goals/{id}` | 已登录 | 更新目标（仅本人） |
| DELETE | `/api/goals/{id}` | 已登录 | 软归档（status=ARCHIVED，仅本人） |

## 规划历史

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/planning/history` | 已登录 | 当前用户的 AI 交互历史 |
| GET | `/api/planning/history/{id}` | 已登录 | 历史详情（仅本人） |

## 问答

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/qa/health` | 无 | LLM 健康检查（返回 provider/model/enabled 状态） |
| POST | `/api/qa/ask` | 已登录 | 提问，返回回答和 historyId |

## 对话 (Chat)

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/chat/send` | 已登录 | 发送消息，返回 AI 回复 |
| GET | `/api/chat/history` | 已登录 | 获取当前用户的对话历史 |
| DELETE | `/api/chat/context` | 已登录 | 清除当前用户的对话上下文 |
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
- **需要 ADMIN 角色**：反馈管理、用户管理、封禁操作
- **需要认证**：`/api/users/current`, `/api/users/logout`, `/api/profiles/**`, `/api/goals/**`, `/api/planning/**`, `/api/qa/ask`
- **认证机制**：JSESSIONID Cookie 传递认证状态
- **CORS 限制**：本地开发环境（`http://localhost:*`, `http://127.0.0.1:*`, `https://localhost:*`）
