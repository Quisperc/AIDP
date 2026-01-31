# Spring Boot 3 统一认证系统 (SSO)

本项目演示了一个完整的单点登录 (SSO) 解决方案，包含**认证中心 (Auth Server)** 和 **接入子系统 (Client App)**。项目基于 **Spring Boot 3.2** 和 **Spring Security 6** 构建，使用标准 **OAuth2 / OpenID Connect (OIDC)** 协议。

## 🏗 系统架构

系统由两个独立运行的服务组成：

1.  **认证中心 (Auth Server)**
    *   **端口**: `8080`
    *   **角色**: Identity Provider (IdP)。负责用户管理、登录认证、颁发 Token (JWT)。
    *   **核心技术**: Spring Authorization Server, Spring Data JPA, H2 Database。
2.  **子系统 (Client App)**
    *   **端口**: `8081`
    *   **角色**: Service Provider / Client。依赖认证中心进行登录，并根据 Token 中的信息进行权限控制。
    *   **核心技术**: Spring OAuth2 Client, Spring Web。

---

## 📂 代码结构说明

### 1. 认证中心 (Auth Server)
路径: `src/main/java/cn/civer/authserver`

该模块是 SSO 的核心，负责处理所有协议细节。

*   **`config/AuthorizationServerConfig.java` (核心配置)**
    *   **功能**: 配置 OAuth2 授权服务器的核心组件。
    *   **`securityFilterChain`**: 定义 OIDC 协议端点（如 `/oauth2/token`）的安全拦截链。
    *   **`registeredClientRepository`**: 注册合法的客户端（Client App）。当前配置为内存模式，定义了 `client-id`, `client-secret`, `redirect-uri` 等。
    *   **`jwkSource`**: 生成 RSA 密钥对，用于对 JWT (ID Token / Access Token) 进行签名。
    *   **`jwtTokenCustomizer`**: **关键逻辑**。在生成 Token 时，拦截并注入自定义 Claims。我们将用户的 `roles` (如 `ROLE_ADMIN`) 放入 Token 中，以便客户端能获取权限信息。

*   **`service/CustomUserDetailsService.java`**
    *   **功能**: 实现 Spring Security 标准接口。从数据库加载用户信息，并转换为框架可识别的 `UserDetails` 对象。

*   **`config/DataInitializer.java`**
    *   **功能**: 系统启动时，自动向 H2 内存数据库写入两个测试用户 (`admin` 和 `user`)。

*   **`resources/application.yml`**
    *   **关键配置**: 设置 `server.servlet.session.cookie.name = AUTH_SESSIONID`。这是为了防止在本地 (`localhost`) 运行时，两个服务都使用默认的 `JSESSIONID` 导致 Cookie 覆盖冲突。

### 2. 接入子系统 (Client App)
路径: `client-app/src/main/java/cn/civer/client`

该模块代表需要接入 SSO 的业务系统。

*   **`config/SecurityConfig.java` (核心配置)**
    *   **功能**: 配置 OAuth2 登录逻辑和权限映射。
    *   **`oidcUserService`**: **关键逻辑**。
        1.  当用户登录成功拿到 Token 后，该方法会被调用。
        2.  它从 Token 的 Claims 中提取 `roles` 字段（这是一个自定义字段，由 Auth Server 注入）。
        3.  将这些角色转换为 Spring Security 的 `GrantedAuthority`（权限对象）。
        4.  **作用**: 让子系统能识别 "你是管理员" 还是 "普通用户"，从而使用 `@PreAuthorize` 进行接口保护。

*   **`controller/HomeController.java`**
    *   **功能**: 演示页面。
    *   `/`: 显示当前登录用户的详细信息（JSON）。
    *   `/admin/dashboard`: 受保护接口，只有拥有 `ROLE_ADMIN` 权限的用户才能访问。

*   **`resources/application.yml`**
    *   **关键配置**:
        *   `spring.security.oauth2.client`: 配置 Provider 地址 (`http://127.0.0.1:8080`) 和 Client 凭证。
        *   `cookie.name = CLIENT_SESSIONID`: 同样修改 Cookie 名称以避免冲突。

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

## 🚀 运行与测试指南

### 环境准备
*   JDK 17+
*   Maven 3.x

### 第一步：启动认证中心
在项目根目录 (`c:\Code\AIDP`) 打开终端运行：
```bash
mvn spring-boot:run
```
等待看到 `Started AuthServerApplication`。

### 第二步：启动子系统
打开一个新的终端，进入 client 目录 (`c:\Code\AIDP\client-app`) 运行：
```bash
cd client-app
mvn spring-boot:run
```
等待看到 `Started ClientApplication`。

### 第三步：验证 SSO 流程
**⚠️ 注意：请全程使用 `127.0.0.1` 访问，不要使用 `localhost`，以避免 Cookie 跨域问题。**

#### 场景 1：管理员登录（权限校验成功）
1.  浏览器（建议无痕模式）访问：`http://127.0.0.1:8081`
2.  跳转登录页，通过：`admin` / `password` 登录。
3.  登录成功，返回子系统首页，页面显示 JSON 中包含 `"roles": ["ROLE_ADMIN"]`。
4.  访问受保护接口：`http://127.0.0.1:8081/admin/dashboard`
    *   **结果**: 显示 `{"message": "Welcome Admin!"}` —— **验证成功！**

#### 场景 2：普通用户登录（权限拒绝）
1.  退出登录或新开无痕窗口。
2.  通过：`user` / `password` 登录。
3.  访问受保护接口：`http://127.0.0.1:8081/admin/dashboard`
    *   **结果**: 显示 **WhiteLabel Error Page (Status 403 Forbidden)**。
    *   **说明**: **这是符合预期的！** 因为 `user` 用户没有管理员权限，系统正确拦截了请求。

---

## ❓ 常见问题排查 (Troubleshooting)

### 1. `[authorization_request_not_found]` 错误
*   **常见原因 1 (并发登录)**: 您是否在**同一个浏览器**的**不同标签页**同时尝试登录两个账号？
    *   **原理**: Spring Security 默认将“正在进行的登录请求”保存在 Session 中。如果您开启了第二个登录流程，它会覆盖掉第一个流程的缓存。当第一个流程回调回来时，发现 Session 里存的是第二个流程的信息，导致匹配失败。
    *   **解决**: 测试多账号时，请务必使用 **无痕模式 (Incognito)** 或 **不同的浏览器**（如 Chrome 和 Edge 同时用）。
*   **常见原因 2 (Cookie 冲突)**: 见前文，需确保 Cookie 名称不冲突且 IP 统一。

### 2. 为什么 `user` 访问 `/admin/dashboard` 报错？
*   这不是系统错误，而是**权限控制生效**的证明。Spring Security 默认对于 403 (禁止访问) 错误会显示白色错误页。如果需要更友好的提示，可以后续添加全局异常处理器。

---

## 🔌 如何配置新的客户端 (生产模式)

在切换到 PostgreSQL 数据库模式 (`JdbcRegisteredClientRepository`) 后，新增客户端不再需要修改 Java 代码，而是直接**向数据库插入 SQL 记录**。

### SQL 模板
您可以直接在数据库工具中执行以下 SQL 来添加一个新的客户端（例如 `order-app`）：

```sql
INSERT INTO oauth2_registered_client (
    id, client_id, client_id_issued_at, client_secret, client_secret_expires_at, 
    client_name, client_authentication_methods, authorization_grant_types, 
    redirect_uris, post_logout_redirect_uris, scopes, client_settings, token_settings
) VALUES (
    'uuid-generated-id-2',                         -- ID (主键)
    'order-app',                                   -- Client ID
    NOW(),                                         -- Issued At
    '$2a$10$r.7...hashed.secret...',               -- Client Secret (BCrypt加密后的 'secret')
    NULL,                                          -- Secret Expires At
    'Order Management System',                     -- Client Name
    'client_secret_basic',                         -- Auth Methods
    'authorization_code,refresh_token',            -- Grant Types
    'http://127.0.0.1:8082/login/oauth2/code/oidc-client', -- Redirect URI (注意端口)
    NULL,                                          -- Post Logout Redirect URI
    'openid,profile',                              -- Scopes
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-authorization-consent":true,"settings.client.require-proof-key":false}', -- Client Settings (Json)
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000]}'  -- Token Settings (Json)
);
```

**⚠️ 注意事项**:
1.  **Client Secret**: 必须是 **BCrypt 加密** 后的字符串。也就是 `DataInitializer` 中 `passwordEncoder.encode("secret")` 的结果。
    *   `secret` 的密文 (strength 10) 参考: `$2a$10$HuWl.U9C5.1/.Fq.pY.a..v/V.u.t.u.t.u.t.u.t.u.t.u.t.` (请尽量生成新的)
2.  **Redirect URI**: 必须严格匹配子系统的配置。
3.  **Settings**: 字段是 JSON 格式的序列化数据，建议直接通过 `DataInitializer` 运行一次生成参考数据，或者复制现有数据进行修改。

---

## 🔧 配置文件说明 (YAML Configuration)

### 1. 认证中心 (Auth Server)
文件：`src/main/resources/application.yml`

```yaml
server:
  port: 8080	               # 服务端口
  servlet:
    session:
      cookie:
        name: AUTH_SESSIONID   # 【重要】自定义 Session Cookie 名称

# 自定义客户端配置 (用于 DataInitializer 启动时自动初始化数据)
# 注意：这只是为了首次启动自动创建客户端，数据存入数据库后，此处配置不再影响已存在的客户端
app:
  auth:
    client-id: client-app      # 客户端标识
    client-secret: secret      # 客户端密钥
    redirect-uri: http://127.0.0.1:8081/login/oauth2/code/oidc-client # 合法回调地址
```

### 2. 子系统 (Client App)
文件：`client-app/src/main/resources/application.yml`

```yaml
server:
  port: 8081                   # 服务端口
  servlet:
    session:
      cookie:
        name: CLIENT_SESSIONID # 【重要】自定义 Session Cookie 名称

spring:
  security:
    oauth2:
      client:
        registration:
          oidc-client:        # 注册名称 (Registration ID)
            provider: auth-server
            client-id: client-app       # 对应 Auth Server 配置的 app.auth.client-id
            client-secret: secret       # 对应 Auth Server 配置的 app.auth.client-secret
            # 授权模式：授权码 + 刷新令牌
            authorization-grant-type: authorization_code
            # 回调地址模板，{registrationId} 会自动替换为 oidc-client
            redirect-uri: "http://127.0.0.1:8081/login/oauth2/code/{registrationId}"
            scope:
              - openid
              - profile
        provider:
          auth-server:
            # 【关键】认证中心地址 (Issuer URI)。Client 会请求 /.well-known/openid-configuration 获取端点信息
            issuer-uri: http://127.0.0.1:8080
```

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
| `app.auth.client-id` | `spring.security.oauth2.client.registration.oidc-client.client-id` | ✅ 是 |
| `app.auth.client-secret` | `spring.security.oauth2.client.registration.oidc-client.client-secret` | ✅ 是 |
| `app.auth.redirect-uri` | `spring.security.oauth2.client.registration.oidc-client.redirect-uri` | ✅ 是 (解析后需一致) |

| `app.auth.redirect-uri` | `spring.security.oauth2.client.registration.oidc-client.redirect-uri` | ✅ 是 (解析后需一致) |

只有这三者完全匹配，握手才能成功。

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

**最佳实践**是使用 **`JdbcRegisteredClientRepository`**。
1.  引入 `spring-boot-starter-jdbc` 依赖。
2.  在数据库中创建标准表 `oauth2_registered_client`（Spring Authorization Server 提供了标准建表语句）。
3.  将 Bean 替换为 `return new JdbcRegisteredClientRepository(jdbcTemplate);`。
### 3. 生产环境推荐方案 (数据库模式)
随着子系统增多，写在代码或配置文件里会很难维护（每次新增都要重启）。

**最佳实践**是使用 **`JdbcRegisteredClientRepository`**。
1.  引入 `spring-boot-starter-jdbc` 依赖。
2.  在数据库中创建标准表 `oauth2_registered_client`（Spring Authorization Server 提供了标准建表语句）。
3.  将 Bean 替换为 `return new JdbcRegisteredClientRepository(jdbcTemplate);`。
4.  这样您就可以通过 SQL 或开发一个管理后台，动态地添加、删除子系统，而无需重启服务。

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
    *   访问 `http://127.0.0.1:8081/users`
    *   可以看到用户列表，并能添加新用户（直接写入认证中心数据库）。
2.  **普通用户登录 (User)**: 
    *   访问 `http://127.0.0.1:8081/users`
    *   会看到 **403 Forbidden** 错误（UI 层拦截）。
    *   访问 `http://127.0.0.1:8081/users`
    *   会看到 **403 Forbidden** 错误（UI 层拦截）。
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
### 3. 全局登出 (Global Logout)
以前的 Logout 只是清除了 Client App 的 Cookie，并没有通知 Auth Server，导致“点登录”立刻又进来了。
现在我们实现了 **OIDC RP-Initiated Logout**:
*   当您在 Client App 点击 **Logout**。
*   Client App 会清除本地会话。
*   Client App 自动跳转到 Auth Server 的 `/connect/logout` 端点。
*   Auth Server 清除 SSO 会话。
*   最终跳转回 Client App 的首页，此时您是**彻底登出**状态。




