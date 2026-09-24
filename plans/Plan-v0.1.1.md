# v0.1.1 登录界面集成与 zhitu 改名清单

## 本次集成

- 保留王钰博版本的知途视觉与滑动登录/注册面板，映射到 `/front/login` 和 `/front/register`；旧 `/login` 跳转至用户登录页。
- 原有简洁登录模板改为 `/adminlogin`。它仍调用统一 `/api/users/login`，只有管理员角色能进入 `/admin/**`；分离的是入口页面，不是账户体系。
- 前端对接 LifeComposer 的 Session、CSRF 和 `/api/users/*`；不带入对方项目的 JWT、`/api/auth/*`、JPA 或数据库。
- 新注册页以学号作为 `users.username`，保存姓名、邮箱；允许邮箱或用户名登录。原有 API 客户端可以继续只传用户名和密码，旧用户、导入演示用户、管理员的邮箱/姓名为 `NULL`。
- 邮箱统一小写并设不区分大小写的唯一索引。当前仅用作登录标识；**尚未做邮箱验证、密码找回、邮件发送**，页面不宣称邮箱已验证。

## 验收

1. 无登录会话可打开 `/front/login`、`/front/register`、`/adminlogin`；普通用户仍不能进入 `/admin/**`。
2. 通过新页面注册学号、姓名、邮箱、密码，重复邮箱或非法邮箱会提示；用户名登录和邮箱登录都使用同一个 Session。
3. 临时密码要求修改、CSRF、失败锁定、封禁和登出保持原有规则。
4. 存量 SQLite 在启动时补齐邮箱/姓名列；旧账号可以照旧按用户名登录。
5. 回归测试通过；在浏览器实际走通一次注册 → 登录 → 画像。

## GitHub 仓库更名：LifeComposer → zhitu

这是一份操作清单，不代表本次已经更名。项目负责人或仓库管理员需在 GitHub 上执行仓库设置变更。

### GitHub 设置与外部引用

- [ ] 先确认目标 `t-789/zhitu` 尚未被占用；通知协作者暂停向旧地址 push，确认未合并 PR、正在运行的部署/自动化。
- [ ] GitHub 仓库页面 `Settings → General → Repository name` 改为 `zhitu`（需要仓库管理员权限）；仓库描述、主页链接和 README 徽章同步检查。
- [ ] 各成员在本地仓库执行 `git remote set-url origin git@github.com:t-789/zhitu.git`，再用 `git remote -v`、`git fetch origin` 检查；GitHub 对旧仓库 URL 的重定向不能代替长期更新。
- [ ] 检查仓库的 `Settings → Pages`、自定义域名、GitHub Actions workflow、Secrets/Variables、Environments、部署密钥、Webhooks 和第三方集成，更新任何写死的旧仓库 URL、仓库名或回调地址。当前仓库未见 `.github/` 工作流，但网页设置仍需人工核对。
- [ ] 检查 `Settings → Collaborators and teams` 与 `Branches / Rules`（权限和规则通常保留，仍需确认目标分支与路径规则）；检查保护规则和 CI 是否仍运行。
- [ ] 检查 Issues、PR、Wiki、Projects、Release 链接、演示文档和外部引用；通知成员更新自己的 fork remote、克隆地址与 IDE Git 配置。

### 仓库内代码与部署（后续独立改动）

- [ ] 确定展示名称、Maven artifactId、Java package、Spring `application.name`、环境变量前缀是否都要一起改。**这几项彼此独立**；GitHub 仓库改名不要求立刻搬 Java 包，也不应直接更改现有 `LIFECOMPOSER_INITIAL_ADMIN_PASSWORD` 而导致空库初始化失败。
- [ ] 若要改目录 `LifeComposer/`、`pom.xml` 的 `artifactId/name`、`run.sh`/`import.sh` 路径与文档链接，单独做一次可回滚的重命名 PR，先检查脚本和构建产物名称。
- [ ] 若要改 `org.example.lifecomposer` 包名、`lifecomposer.*` 配置键、数据库文件名和环境变量，逐项制定兼容迁移，再跑全量测试与现有数据库启动验证；不要把它们混在界面集成 PR 中。

## 后续页面工作

注册/登录完成后，正式聊天页与调试页的分离属于下一阶段前端工作；本版本不重写推荐、画像或 Agent 逻辑。协作者提 PR 的流程见根目录 `CONTRIBUTING.md`。
