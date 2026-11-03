# AIDP · 统一认证中心

基于 **Spring Boot 4.0.3 / Spring Authorization Server / Java 22** 的 OIDC 单点登录系统：
统一登录、统一退出（Back-Channel Logout）、客户端自助注册、用户自助改密。

适合「一个主域下挂多个内部系统」的场景：新增一个子系统 = 在管理后台注册一个客户端 + 加一段配置。

---

## 组成

| 模块 | 端口 | 职责 |
|---|---|---|
| `auth-server`（根目录） | 8080 | OIDC 提供方 + 资源服务器，唯一读写认证库的一方 |
| `client-usermanage` | 8081 | 管理端 REST API，转发调用认证中心的 `/api/**` |
| `client-usermanage/frontend` | 5173（开发） | Vue 3 管理后台 SPA，生产托管在 EdgeOne Pages |

```
                    ┌──────────────────────┐
   浏览器 ──────────▶│ admin.civer.cn (SPA) │
     │              └──────────┬───────────┘
     │ Cookie + XSRF           │ fetch /api, /admin
     │                         ▼
     │              ┌──────────────────────┐   Bearer   ┌───────────────────┐
     ├─────────────▶│ um.civer.cn   (8081) │───────────▶│ idp.civer.cn(8080)│
     │   OIDC 登录  └──────────────────────┘            │  SAS + Resource   │
     └────────────────────────────────────────────────▶│      Server       │
                                                       └─────────┬─────────┘
                                                                 ▼
                                                          PostgreSQL / Redis
```

---

## 本地启动

前置：JDK 22+、Maven 3.9+、PostgreSQL（库 `authdb`）、可选 Redis、pnpm（改前端才需要）。

```bash
# 1. 数据库（或用你自己的实例）
docker compose up -d          # 起 postgres:18，映射 5432

# 2. 敏感配置：根目录建 application-secret.yml（已 gitignore）
cat > application-secret.yml <<'YAML'
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/authdb
    username: <user>
    password: <password>
YAML

# 3. 认证中心（启动时 Flyway 自动建表，并注册初始客户端）
mvn spring-boot:run

# 4. 管理端后端
cd client-usermanage && mvn spring-boot:run

# 5. 管理端前端（可选）
cd client-usermanage/frontend && pnpm install --ignore-scripts && pnpm dev
```

首次启动会自动创建演示账号（`app.bootstrap.seed-demo-users=true`）。
生产必须关掉：`APP_BOOTSTRAP_SEED_DEMO_USERS=false`。

跑测试：

```bash
mvn test          # 14 个回归测试，含权限拒绝与开放重定向
```

---

## 目录

```
├── src/main/java/cn/civer/authserver/
│   ├── config/        安全、令牌、密钥、Flyway、缓存、初始数据
│   ├── controller/    OIDC 端点、/api/users、/api/clients、同意页、退出
│   ├── service/       用户/客户端/全局登出业务逻辑
│   ├── handler/       登录成功、登出成功、全局异常
│   └── dto/ entity/ repository/
├── src/main/resources/
│   ├── db/migration/  Flyway 迁移（V1 建表 + 索引）
│   ├── templates/     登录/同意/错误页（Thymeleaf，服务端渲染）
│   └── static/css/    hig.css（Apple HIG 设计语言）
├── client-usermanage/
│   ├── src/main/java/ 管理端后端（无数据库，纯转发）
│   └── frontend/      Vue 3 + Vite + TypeScript
├── deploy/            服务器侧目录/端口/环境变量约定
├── docs/              见下
└── .cnb.yml           CNB 发布流水线（按 tag 前缀分流）
```

---

## 文档

| 文档 | 什么时候看 |
|---|---|
| [`docs/架构说明.md`](docs/架构说明.md) | 想搞懂链路、多实例、为什么这么设计 |
| [`docs/接入规范.md`](docs/接入规范.md) | 要把一个新系统接进 SSO |
| [`docs/部署与运维.md`](docs/部署与运维.md) | 要发版、改配置、排障、回滚 |
| [`docs/故障复盘.md`](docs/故障复盘.md) | 遇到 500/403/登出异常，先查这里 |
| [`deploy/README.md`](deploy/README.md) | 换服务器 / 重装时要核对的前提 |
| [`docs/archive/`](docs/archive) | 历史审查报告与上线记录（只读，不再更新） |

---

## 发布

推标签即发布，标签前缀决定发哪个服务：

```bash
git tag auth-v2.3.9 && git push origin auth-v2.3.9   # 认证中心
git tag user-v2.3.9 && git push origin user-v2.3.9   # 管理端后端
```

链路：GitHub → Actions 同步标签到 CNB → CNB 流水线构建 → rsync 到服务器 → 重启 → 健康校验。
前端不在链路内，需手工发布到 EdgeOne Pages，步骤见 `docs/部署与运维.md`。

---

## 备案信息

ICP `渝ICP备2026020534号` · 公安备案 `渝公网安备50019002505695号`

两处必须同步维护：`src/main/resources/application.yml` 的 `app.footer.*`，
以及 `client-usermanage/frontend/src/App.vue` 的 `ICP_TEXT` / `POLICE_TEXT` / `POLICE_URL`。
