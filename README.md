# Spring Boot 3 OIDC 统一认证系统 (SSO)

本项目演示了一个基于 **Spring Boot 3.2** 和 **Spring Authorization Server (SAS)** 的完整单点登录 (SSO) 解决方案。系统实现了标准的 **OIDC (OpenID Connect)** 协议，支持多客户端接入、统一登录、统一退出（Back-Channel Logout）。

## 🏗 系统架构

系统由三个独立模块组成：

1.  **认证中心 (Auth Server)** - `auth-server`
    *   **端口**: `8080`
    *   **职责**:
        *   **IdP (Identity Provider)**: 负责用户认证、颁发 JWT (Check Token)。
        *   **Resource Server**: 提供 `/api/users` 等管理接口。
        *   **OIDC Provider**: 提供标准 OIDC 端点（Discovery, JWKS, UserInfo）。
    *   **存储**: PostgreSQL (用户、客户端、授权信息持久化)。

2.  **管理后台 (Client UserManage)** - `client-usermanage`
    *   **端口**: `8081`
    *   **职责**: 系统管理员后台。通过 OAuth2 登录，使用 Feign 调用 Auth Server API 管理用户和客户端。

3.  **接入示例子系统 (Client Template)** - `client-template`
    *   **端口**: `8089` (默认)
    *   **职责**: 为新业务系统提供“开箱即用”的标准接入模板。

---

## 🔗 接入逻辑与流程

当用户访问子系统时，发生的完整流程如下：

1.  **未登录拦截**: 用户访问 `http://127.0.0.1:8081`，Client App 发现用户未认证。
2.  **重定向 (Redirect)**: Client App 将用户浏览器重定向到认证中心 `http://127.0.0.1:8080/oauth2/authorize`。
3.  **用户登录**: 用户在 8080 端口输入账号密码。认证中心验证通过。
4.  **授权码回调**: 认证中心生成一个 Authorization Code，并将浏览器重定向回 Client App 的回调地址 (`/login/oauth2/code/oidc-client`)。
5.  **换取 Token (Back-channel)**: Client App 后台自动使用 Code 向认证中心换取 `Access Token` 和 `ID Token`。
6.  **权限解析**: Client App 的 `SecurityConfig` 解析 Token，提取 `roles`。
7.  **会话建立**: Client App 创建自己的 Session (`CLIENT_SESSIONID`)，用户完成登录。

---

## 🚀 快速启动指南

### 1. 环境准备
*   JDK 17+
*   Maven 3.x
*   PostgreSQL 数据库 (创建数据库 `authdb`)

### 2. 数据库与敏感配置
在 `src/main/resources/application.yml` 中保留数据库连接结构，**不要**在仓库内提交真实密码。
**🔒 安全建议**: 在项目根目录创建 `application-secret.yml`（已加入 `.gitignore`），仅覆盖敏感与环境相关配置，例如：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://HOST:5432/DBNAME
    username: YOUR_USER
    password: YOUR_REAL_PASSWORD
  security:
    oauth2:
      authorizationserver:
        issuer: ${APP_BASE_URL:https://idp.civer.cn}  # 显式指定 OIDC Issuer
app:
  base-url: ${APP_BASE_URL:https://idp.civer.cn}
  auth:
    initial-client:
      client-id: client-usermanage
      client-secret: secretforusermanage
      redirect-uris: https://um.civer.cn/login/oauth2/code/oidc-client
      post-logout-redirect-uri: https://idp.civer.cn/login
```
生产部署时优先使用环境变量 `APP_BASE_URL` 覆盖，避免在文件中写死域名。

### 3. 初始化数据 (Scripts)
所有 SQL 脚本和工具现已归档至 `scripts/` 目录。

1.  **表结构**: 系统启动时会自动由 Hibernate 创建/更新 (`ddl-auto: update`)。
2.  **初始数据**: `scripts/insert_client_user.sql` 和 `scripts/insert_clients.sql` 提供了初始客户端数据。
3.  **生成客户端 SQL**:
    *   运行 `scripts/ClientSqlGenerator.java` (main 方法)。
    *   按提示输入 Client ID、Secret 和 端口。
    *   它会自动生成包含 **BCrypt 加密 Secret** 和 **标准 OIDC 配置** 的 `INSERT` 语句。

### 4. 启动服务
```bash
# 1. 启动认证中心 (8080)
mvn spring-boot:run -pl . 

# 2. 启动管理后台 (8081)
cd client-usermanage
mvn spring-boot:run

# 3. 启动示例客户端 (8089)
cd client-template
mvn spring-boot:run
```

---

## 🔐 核心功能特性

### 1. 客户端注册 (Client Registration)
不再支持内存模式。所有客户端必须注册在数据库 `oauth2_registered_client` 表中。
*   **管理方式**:
    *   **推荐**: 使用管理后台 `http://127.0.0.1:8081/admin/clients` 进行图形化注册。
    *   **手动**: 使用 `scripts/ClientSqlGenerator.java` 生成 SQL 插入。

### 2. OIDC Back-Channel Logout (统一退出)
实现了 OIDC 标准的后端广播退出机制，比传统的 Session 清除更安全、彻底。

*   **流程**:
    1.  用户在任意子系统点击退出。
    2.  Auth Server 收到退出请求，清除 SSO Session。
    3.  Auth Server 根据数据库记录，向 **所有** 该用户登录过的子系统发送 HTTP POST 请求 (`/api/sso-logout`)。
    4.  请求体包含一个 **Logout Token** (JWT)，由 Auth Server 私钥签名。
    5.  子系统验证 JWT 签名和 Claims (Audience, Issuer)，验证通过后销毁本地 Session。

### 3. 配置与隐私保护
*   **Auth Server**：根目录 `application.yml` 通过 `spring.config.import: optional:file:./application-secret.yml` 加载 `application-secret.yml`。其中应覆盖：数据库连接、`spring.security.oauth2.authorizationserver.issuer`（显式指定 OIDC Issuer，保证 Discovery 与 Token 中 `iss` 一致）、`app.base-url` 及 `app.auth.initial-client` 等生产用值。
*   **Client（client-usermanage / client-template）**：各模块的 `application.yml` 同样支持 `optional:file:./application-secret.yml`。secret 中只需覆盖 **client-secret** 和 **app.auth-server-url / app.base-url**，其余 OAuth2 结构沿用主配置，避免重复。
*   所有 `application-secret.yml` 均已加入 `.gitignore`，不会提交到 Git。

---

## 📁 目录结构

*   `src/main/java/cn/civer/authserver` - **认证中心源码**
*   `client-usermanage/` - **管理后台源码**
*   `client-template/` - **标准客户端模板** (复制此目录即可开发新系统)
*   `scripts/` - **SQL 脚本与工具类**
    *   `ClientSqlGenerator.java`: 交互式 SQL 生成器
    *   `insert_clients.sql`: 初始数据备份
*   `application-secret.yml` - (需手动创建) 根目录为 Auth Server 敏感配置；`client-usermanage/`、`client-template/` 下可各有自己的 `application-secret.yml`，仅覆盖 client-secret 与 app 地址即可。

---

## 🌐 生产部署：跨子域名 SSO

当您需要部署到生产环境，实现 `c1.civer.cn`、`c2.civer.cn` 等多个子系统共享 `idp.civer.cn` 的统一登录时，需要进行以下配置。

### 1. 架构规划

| 服务 | 域名 | 说明 |
|------|------|------|
| Auth Server (IDP) | `idp.civer.cn` | 认证中心 |
| Client UserManage | `um.civer.cn` | 管理后台 |
| 业务系统 A | `c1.civer.cn` | 子系统 |
| 业务系统 B | `c2.civer.cn` | 子系统 |

### 2. 关键配置

#### Auth Server：显式指定 Issuer
反向代理后请求多为 HTTP，Discovery 中的 `issuer` 会错误变为 `http://...`。必须在 Auth Server 的 `application-secret.yml` 或环境变量中显式指定：
```yaml
spring:
  security:
    oauth2:
      authorizationserver:
        issuer: https://idp.civer.cn
```
或通过环境变量 `APP_BASE_URL=https://idp.civer.cn` 覆盖（若 secret 中已使用 `${APP_BASE_URL:https://idp.civer.cn}`）。

#### Cookie Domain（实现 Session 共享）
```yaml
server:
  servlet:
    session:
      cookie:
        domain: civer.cn  # 允许所有子域名共享
```

#### 环境变量（生产部署）
```bash
# Auth Server（认证中心）
APP_BASE_URL=https://idp.civer.cn

# Client UserManage（管理后台）
APP_AUTH_SERVER_URL=https://idp.civer.cn
APP_BASE_URL=https://um.civer.cn

# 其他 Client（如 client-template 或自建子系统）
APP_AUTH_SERVER_URL=https://idp.civer.cn
APP_BASE_URL=https://c1.civer.cn   # 该子系统对外访问地址
```
Client 只需配置上述两个变量即可同时生效：OIDC 发现（issuer-uri）、Feign 调用 Auth Server 的 base URL、以及退出时跳转的认证中心地址（由代码从 OAuth2 注册信息中的 issuer 推导）。

### 3. HTTPS + 反向代理

生产环境推荐使用 Caddy 或 Nginx 作为反向代理：

```
                    ┌─────────────────────────────────────┐
                    │         Caddy / Nginx               │
                    │      (SSL 终止 + 反向代理)           │
                    └─────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
   idp.civer.cn:443    um.civer.cn:443    c1.civer.cn:443
          │                   │                   │
          ▼                   ▼                   ▼
    localhost:8080     localhost:8081     localhost:8082
```

**Caddy 配置示例 (`Caddyfile`)：**
```
idp.civer.cn {
    reverse_proxy localhost:8080
}

um.civer.cn {
    reverse_proxy localhost:8081
}
```

建议在反向代理中设置 `X-Forwarded-Proto: https` 和 `X-Forwarded-Host`，并在 Auth Server 的 `application.yml` 中启用：
```yaml
server:
  forward-headers-strategy: framework
```
这样 Spring 能正确识别外部协议与 Host，与显式 `issuer` 配置一起保证行为一致。

### 4. 容器部署（Docker）
*   **环境变量**：容器启动时务必传入 `APP_BASE_URL`（Auth Server）或 `APP_AUTH_SERVER_URL`、`APP_BASE_URL`（Client），否则会使用默认的 localhost 地址，导致 Discovery 与回调异常。
*   **挂载 secret**：若通过挂载提供 `application-secret.yml`，请挂载到容器内 Spring Boot 工作目录（如 `/app/application-secret.yml`），与 `spring.config.import: optional:file:./application-secret.yml` 对应。

### 5. 数据库与客户端 redirect_uris

确保在 Auth Server 数据库的 `oauth2_registered_client` 表中，每个客户端的 `redirect_uris` 包含正确的生产域名：
```
https://um.civer.cn/login/oauth2/code/oidc-client
https://c1.civer.cn/login/oauth2/code/oidc-client
```

---

## 📦 快速接入 (Client Template)

为了简化新子系统的接入流程，我们提供了一个开箱即用的模板工程：`client-template`。

### 模板结构

| 文件 | 说明 |
|------|------|
| `ClientApplication.java` | Spring Boot 启动类 |
| `config/SecurityConfig.java` | OAuth2 安全配置（登录、角色映射、退出）|
| `controller/HomeController.java` | 首页控制器（展示用户信息）|
| `controller/LoginController.java` | 登录页（已登录自动跳转）|
| `controller/SsoLogoutController.java` | SSO 广播退出接收端点 |
| `templates/index.html` | 首页模板 |
| `templates/login.html` | 登录页模板 |

### 预置功能
1.  **OAuth2 登录**: 自动对接 Auth Server
2.  **自定义登录页**: 已登录用户访问 `/login` 自动跳转首页
3.  **SSO 退出**: 支持单客户端退出和全局广播退出
4.  **防 Cookie 冲突**: 独立的 Session Cookie 名称

### 接入步骤 (5分钟完成)

1.  **复制项目**: 复制 `client-template` 文件夹，重命名为您的新项目名（例如 `client-oa`）

2.  **修改 `pom.xml`**: 将 `artifactId` 和 `name` 修改为 `client-oa`

3.  **修改配置**: 在 `application.yml` 中设置端口、cookie 名称、client-id 等；敏感与生产地址放在同目录下的 `application-secret.yml`（需自行创建，不会打入 jar）。Client 只需在 secret 中覆盖 **client-secret** 以及 **app.auth-server-url / app.base-url** 即可。
    | 配置项 | 说明 / 示例值 |
    |--------|----------------|
    | `server.port` | 本服务端口，如 `8082` |
    | `server.servlet.session.cookie.name` | 唯一 Cookie 名，如 `OA_SESSIONID` |
    | `spring.security.oauth2.client.registration.oidc-client.client-id` | 与 Auth Server 注册一致，如 `oa-system` |
    | `spring.security.oauth2.client.registration.oidc-client.client-secret` | 在 secret 中覆盖，不提交仓库 |
    | `app.auth-server-url` | 认证中心地址，如 `https://idp.civer.cn`，可用 `APP_AUTH_SERVER_URL` 覆盖 |
    | `app.base-url` | 本系统对外地址，如 `http://127.0.0.1:8082`，可用 `APP_BASE_URL` 覆盖 |

4.  **注册客户端**: 在 Auth Server 数据库中注册该客户端
    *   **推荐**: 使用管理后台 `http://127.0.0.1:8081/admin/clients`
    *   **手动**: 运行 `scripts/ClientSqlGenerator.java` 生成 SQL

5.  **启动开发**: `mvn spring-boot:run` 即可加入 SSO 生态

---

## 🔐 退出机制详解 (Logout Architecture)

系统实现了两种不同层级的退出逻辑，以满足企业级业务需求：

### 1. SSO 全局退出 (Global Logout)
*   **触发**: 在 SSO (8080) 页面点击退出。
*   **行为**: **"全链路安全退出 (OIDC Back-Channel Logout)"**
    *   **Clear Consent**: 立即删除该用户在数据库中的所有授权记录。
    *   **Broadcast**: Auth Server 向所有注册 Client 的 `/api/sso-logout` 发送 POST 请求。
        *   **Logout Token**: 生成一个符合 **OpenID Connect Back-Channel Logout 1.0** 规范的 **JWT**。
        *   **安全验证**: 由 Auth Server 私钥签名。
    *   **Verify & Invalidate**: Client App (如 8081) 接收到 JWT 后：
        1.  使用 Auth Server 的公钥 (`/oauth2/jwks`) 验证签名。
        2.  校验 `iss` (Issuer) 和 `aud` (Audience)。
        3.  提取 `sub` (用户名) 并通过 `SessionRegistry` 销毁对应 Session。
*   **效果**: 所有系统同时掉线。用户下次刷新页面时，会自动跳转回首页。

### 2. 子系统退出 (Single Client Logout)
*   **触发**: 在子系统 (如 8081) 点击退出。
*   **行为**: **"定点清除"**
    *   **Local Logout**: 子系统销毁自己的本地 Session。
    *   **Revoke Consent**: 子系统跳转到 `8080/oauth2/revoke-consent`，只删除**当前子系统**的授权记录。
    *   **Keep SSO**: Auth Server 的 Session **保留**。
*   **效果**:
    *   **当前系统**: 下次进入时，因为授权已删，会跳转 Auth Server，虽无需输密码（SSO 在），但**必须重新点击“同意授权”**。
    *   **其他系统**: 保持登录状态，不受影响。

---

## 📖 深入理解：客户端注册 (Client Registration)

您可能会困惑：**什么是“注册客户端”？为什么需要它？**

可以将 OAuth2 的“客户端”理解为**“想要使用认证服务的应用程序”**。

### 1. 为什么需要注册？(RegisteredClientRepository)
认证中心 (Auth Server) 不会信任任何随便发来的请求。就像你需要注册账号才能登录系统一样，**子系统 (Client App)** 也必须在认证中心“备案”才能使用 SSO 服务。

`RegisteredClientRepository` 就是这个“备案录”。在本项目中，我们使用 `InMemoryRegisteredClientRepository` 在内存中存储了一个客户端信息（生产环境通常存数据库）。

### 2. 核心参数详解

*   **Client ID (`client-id`)**:
    *   **含义**: 相当于子系统的“用户名”。
    *   **作用**: 当子系统向认证中心发起请求时，它会说“我是 `client-app`”。认证中心会去查找有没有这个 ID。

*   **Client Secret (`client-secret`)**:
    *   **含义**: 相当于子系统的“密码”。
    *   **作用**: 只有 ID 是不够的，子系统在请求 Token 时（Step 5: Back-channel），必须带上这个密码，证明它真的是 `client-app`，而不是冒充者。

*   **Redirect URI (`redirect-uri`)**:
    *   **含义**: **白名单安全机制**。
    *   **作用**: 当用户登录成功后，认证中心需要把用户“送回”子系统。但是送回哪里呢？为了防止钓鱼攻击（黑客把用户骗到一个假网站），认证中心**只允许**重定向到预先配置好的地址。如果请求中的 `redirect_uri` 与配置不符，认证中心会直接报错。

*   **授权类型 (`Authorization Grant Type`)**:
    *   **`authorization_code` (授权码模式)**: 最安全的模式。用户只能看到一个临时的“Code”，真正的 Token 是子系统在后台用 Code + Secret 换来的，Token 不会暴露在浏览器中。
    *   **`refresh_token`**: 允许子系统在 Access Token 过期后，自动刷新获取新 Token，而不需要用户重新登录。

*   **Scope (`scope`)**:
    *   **含义**: 申请的权限范围。
    *   `openid`: 表示即使只是为了“验证身份” (OIDC)。
    *   `profile`: 表示想获取用户的基本资料 (用户名等)。

### 3. 配置对应关系

| 认证中心 (Auth Server) | 子系统 (Client App) | 必须一致? |
| :--- | :--- | :--- |
| `app.auth.initial-client.client-id` / 数据库 | `spring.security.oauth2.client.registration.oidc-client.client-id` | ✅ 是 |
| `app.auth.initial-client.client-secret` / 数据库 | `spring.security.oauth2.client.registration.oidc-client.client-secret` | ✅ 是 |
| `redirect_uris` (数据库) | `redirect-uri: ${app.base-url}/login/oauth2/code/{registrationId}` 解析后 | ✅ 是 |

只有这三者完全匹配，握手才能成功。

**区分两个 ID**：配置里的 **`oidc-client`** 是 Spring 本地 **registrationId**（用于 `findByRegistrationId("oidc-client")`、回调路径 `/login/oauth2/code/oidc-client`），不会发给 Auth Server；**`client-id: client-usermanage`** 才是 OAuth2 协议中的客户端标识，与 Auth Server 数据库中的客户端一致。

---

## 🚀 进阶：如何支持多个子系统？

如果您有多个子系统（例如 `order-system` 运行在 8081, `oa-system` 运行在 8082）都需要接入 SSO，逻辑是非常直观的。

### 1. 核心原理
认证中心的 `RegisteredClientRepository` 就像一个**白名单**。如果有 10 个子系统，您就需要在这个白名单里注册 10 个 `RegisteredClient` 对象。

### 2. 代码实现方式 (内存模式示例)
在 `AuthorizationServerConfig.java` 中，您可以创建多个 Client 并一次性注册：

```java
@Bean
public RegisteredClientRepository registeredClientRepository() {
    // 定义子系统 A
    RegisteredClient clientA = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("client-a")
        .clientSecret(encoder.encode("secret-a"))
        .redirectUri("http://127.0.0.1:8081/...")
        .build();

    // 定义子系统 B
    RegisteredClient clientB = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("client-b")
        .clientSecret(encoder.encode("secret-b"))
        .redirectUri("http://127.0.0.1:8082/...")
        .build();

    // 同时注册多个
    return new InMemoryRegisteredClientRepository(clientA, clientB);
}
```

### 3. 生产环境推荐方案 (数据库模式)
随着子系统增多，写在代码或配置文件里会很难维护（每次新增都要重启）。

**最佳实践**是使用 **`JdbcRegisteredClientRepository`** 实现数据库持久化。
1.  引入 `spring-boot-starter-jdbc` 依赖。
2.  客户端信息存储在 `oauth2_registered_client` 表中，在数据库中创建标准表 `oauth2_registered_client`（Spring Authorization Server 提供了标准建表语句）。
3.  将 Bean 替换为 `return new JdbcRegisteredClientRepository(jdbcTemplate);`。
4.  支持通过管理后台 `/admin/clients` 动态添加、修改、删除客户端。

---

## 👤 用户管理系统 (Phase 2)

现在，认证中心 (Auth Server) 已升级为 **资源服务器 (Resource Server)**，提供用户管理的 REST API。而子系统 (Client App) 则提供了管理界面。

### 1. 架构逻辑
1.  **Client App (8081)**: 用户访问 `/users` 页面。
2.  **WebClient**: 自动获取当前登录用户的 `Access Token`。
3.  **API 调用**: Client App 携带 Token 向 Auth Server 发起 `GET /api/users` 请求。
4.  **Auth Server (8080)**:
    *   验证 Token 签名是否合法。
    *   检查 Token 中是否包含 `ROLE_ADMIN` 权限。
    *   返回用户列表 JSON。

### 2. 功能验证
1.  **管理员登录 (Admin)**: 
    *   访问 `http://127.0.0.1:8081/admin/users`
    *   可以看到用户列表，并能添加新用户（直接写入认证中心数据库）。
2.  **普通用户登录 (User)**: 
    *   访问 `http://127.0.0.1:8081/admin/users`
    *   会看到 **403 Forbidden** 错误（无管理员权限）。
    *   即使直接调用 API，也会被 Auth Server 拦截。

---

## 🛠️ 用户自助服务 (Phase 4)

支持用户修改自己的**用户名**和**密码**。

### 1. 核心逻辑 (`PUT /api/users/me`)
为了安全起见，我们增加了一个专门的 API `me`，而不是复用 `PUT /api/users/{id}`。
*   Auth Server 会自动从 Token 中获取当前用户的 `username`。
*   用户只能修改**自己的**信息。
*   API 层面**严格忽略**了 `role` 和 `enabled` 字段的修改请求，防止普通用户提权。

### 2. 页面交互
*   **入口**: 访问 `/profile` 页面，或者从“用户管理”页面的右上角进入（仅限管理员）。
*   **修改密码**: 留空则不修改。
*   **修改用户名**: 一旦修改成功，系统会强制您**重新登录**（因为 access token 中的 `sub` 字段失效了）。

### 3. 全局登出 (Global Logout)
以前的 Logout 只是清除了 Client App 的 Cookie，并没有通知 Auth Server，导致“点登录”立刻又进来了。
现在的流程如下：
*   用户在 Client App 点击 **Logout**。
*   Client App 清除本地会话 (`CLIENT_SESSIONID`)。
*   Client App 自动跳转到 Auth Server 的 `/connect/logout` 端点。
*   Auth Server 清除 SSO 会话 (`AUTH_SESSIONID`)。
*   Auth Server 根据配置 (`post-logout-redirect-uri`) 将用户重定向回 Client App 的首页。
*   由于此时双方会话均已清除，用户处于**彻底登出**状态。

---

## 🔒 安全增强 (Bug Fixes)

我们在开发过程中修复了几个关键的安全和逻辑问题：

1.  **防止密码双重哈希 (Double Hashing)**
    *   **问题**: Client App 更新用户状态时，不小心把 Auth Server 返回的“已加密密码”又发回去了，导致 Auth Server 再次加密，密码彻底乱掉。
    *   **修复**: 在 Auth Server 的 `User` 实体中，对密码字段加上了 `@JsonProperty(access = WRITE_ONLY)`。这确保了密码**只能写入**（修改时），**绝不会读取**（查询 API 永远不返回密码字段），从根源上解决了问题。

2.  **稳健的更新逻辑 (Fetch-Modify-Save)**
    *   **修复**: Client App 的更新操作不再依赖前端传递所有字段。现在改为先从服务器拉取最新数据，只修改变化的字段（如 `enabled` 状态），然后写回。这避免了因前端表单字段缺失导致的数据损坏。

3.  **UI/UX 优化**
    *   **排序**: 用户列表强制按 ID 排序，防止刷新后乱序。
    *   **交互**: 修复了按钮点击区域过小的问题，优化了表格布局。

4.  **生产环境 Issuer 与配置简化**
    *   **Auth Server**：通过 `spring.security.oauth2.authorizationserver.issuer` 显式指定 OIDC Issuer，避免反向代理后 Discovery 返回 `http://`。`app.base-url` 仅用于业务侧拼链接，不参与 SAS 的 issuer 计算。
    *   **Client**：退出跳转的认证中心地址由代码从已注册的 OAuth2 Client 的 `issuer-uri` 推导，与 `issuer-uri` 配置一致，无需单独维护一份“认证中心 URL”；Feign 仍通过 `app.auth-server-url`（与 issuer-uri 同源）配置 base URL。
