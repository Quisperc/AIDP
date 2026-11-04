# AIDP · 统一认证中心

基于 **Spring Boot 4.0.3 / Spring Authorization Server / Java 22** 的 OIDC 单点登录系统：
统一登录、全局退出（服务端令牌吊销 + RP-Initiated Logout）、客户端自助注册、用户与账号自助管理。

**一个应用、一个 jar、一个域名**：认证中心与管理台在同一个进程里，管理台用服务端渲染的
Thymeleaf 页面，不引入前端工程与跨域链路。适合「一个主域下挂多个内部系统」的场景。

---

## 组成

| 部分 | 路径 | 说明 |
|---|---|---|
| OIDC 提供方 | `src/main/java/.../config` | 授权码 + PKCE、Discovery、JWKS、UserInfo |
| 登录 / 同意 / 错误页 | `templates/` | Thymeleaf 服务端渲染（OAuth2 要求用户代理与授权服务器同源交互） |
| 管理台 | `templates/admin/` | 用户、客户端、审计三类页面，仅 `ROLE_ADMIN` 可访问 |
| 账号自助 | `templates/account/` | 资料、改密、改登录名、已授权应用，任意已登录用户可访问 |
| 资源 | `static/css/` | `tokens.css`（设计变量）+ `hig.css`（认证页）+ `admin.css`（管理台） |

```
                       ┌────────────────────────────────────────────┐
  浏览器 ─────────────▶│  idp.civer.cn         （单一 Spring Boot）  │
                       │                                            │
                       │  /login /oauth2/consent   表单登录 + 同意   │
                       │  /admin/**                管理台（ROLE_ADMIN）│
                       │  /account/**              账号自助（已登录） │
                       │  /oauth2/** /.well-known  对接子系统         │
                       └───────────────┬────────────────────────────┘
                                       │
                          PostgreSQL + Redis（HTTP 会话 / 登录失败计数）
```

第三方子系统仍是独立的 OIDC 客户端，接入方式见 [`docs/接入规范.md`](docs/接入规范.md)。

---

## 本地启动

前置：JDK 22+、Maven 3.9+、PostgreSQL（库 `authdb`）。生产会话与缓存走 Redis；
本地若没有 Redis，按下方说明排除自动配置即可。

```bash
# 1. 数据库（账号 / 库名可用 DB_URL / DB_USERNAME 覆盖）
export DB_PASSWORD='<自行设定的强口令>'   # 不设会直接报错，避免弱默认口令
docker compose up -d                    # postgres:18，仅监听 127.0.0.1:5432

# 2. 启动（Flyway 自动建表）
mvn spring-boot:run
```

数据源、issuer 等全部由环境变量提供，仓库内不放任何凭据文件。空库想要演示账号时，
启动前设 `APP_BOOTSTRAP_SEED_DEMO_USERS=true`，用 `admin` / `password` 登录
（口令可用 `APP_BOOTSTRAP_DEMO_USER_PASSWORD` 改写）。

> Spring Boot 4 起，classpath 上有 `spring-boot-starter-session-data-redis` 就会自动把
> HttpSession 存进 Redis（已移除 `spring.session.store-type`）。本地没有 Redis 时追加：
> `mvn spring-boot:run -Dspring-boot.run.arguments=--spring.autoconfigure.exclude=org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration`
> 集成测试已在 `application-test.yml` 里做了同样的排除。

打开 <http://127.0.0.1:8080/login>，点「进入管理台」或「账号设置」。

测试：

```bash
mvn test      # 44 个用例：越权、开放重定向、密码不外泄、密钥持久化、登出吊销、登录限流、分页、审计、模板渲染、账号自助、单客户端撤销、PKCE 开关、完整授权同意链路、ID Token 的 sub 与条件 claim
```

演示账号由 `app.bootstrap.seed-demo-users` 控制，**默认关闭**（漏配环境变量时宁可没人能登录，
也不要带着 `admin/password` 上线）。生产请自行初始化账号。

---

## 目录

```
├── src/main/java/cn/civer/authserver/
│   ├── config/       安全链、OIDC 存储、令牌装配、Flyway、缓存、启动初始化
│   ├── security/     签名密钥的持久化读写（SigningKeyStore）
│   ├── controller/   OIDC 端点 + 管理台（AdminUser / AdminClient / AdminAudit）
│   │                 + 账号自助（AccountController）+ 同意页
│   ├── service/      用户、客户端、账号自助、全局登出、登录限流、操作审计
│   ├── repository/   用户/审计仓储 + 客户端与授权记录投影
│   └── dto/ entity/ exception/ handler/
├── src/main/resources/
│   ├── db/migration/ Flyway 迁移（V1 基础表 / V2 审计表 / V3 账号资料与稳定 sub）
│   ├── templates/    认证页 + admin/（管理台）+ account/（账号自助）+ fragments/
│   └── static/css/   tokens / hig / admin
├── deploy/           服务器侧目录、端口、环境变量约定
├── docs/             见下
└── .cnb.yml          发布流水线（打 tag 即发布）
```

---

## 文档

| 文档 | 什么时候看 |
|---|---|
| [`docs/架构说明.md`](docs/架构说明.md) | 想搞懂链路、Redis 会话、为什么管理台不单独部署 |
| [`docs/外部系统接入.md`](docs/外部系统接入.md) | **非本仓库的系统**要接进来：怎么取 issuer / discovery / JWKS，任意语言的最小流程 |
| [`docs/接入规范.md`](docs/接入规范.md) | Spring Boot 子系统接入（含标准退出与 roles 映射示例） |
| [`docs/部署与运维.md`](docs/部署与运维.md) | 要发版、选部署方式、排障、回滚 |
| [`docs/配置参考.md`](docs/配置参考.md) | 全部环境变量 / 默认值 / 优先级、无 Redis 单机跑、配置错误排查 |
| [`docs/故障复盘.md`](docs/故障复盘.md) | 遇到 500/403/登出异常，先查这里 |
| [`deploy/README.md`](deploy/README.md) | 换服务器 / 重装时要核对的前提 |
| [`docs/archive/`](docs/archive) | 历史审查报告与上线记录（只读，不再更新） |

---

## 发布

```bash
git tag auth-vX.Y.Z && git push origin auth-vX.Y.Z   # X.Y.Z 为三段式版本号
```

链路：GitHub → Actions 同步标签到 CNB → CNB 流水线（测试 + 构建 + 制品）→ rsync 到服务器
→ 重启容器 → 健康校验。管理台随 jar 一起发布，没有额外的前端构建与部署步骤。

---

## 备案信息

ICP `渝ICP备2026020534号` · 公安备案 `渝公网安备50019002505695号`

维护位置：`application.yml` 的 `app.footer.*` 与 `templates/fragments/footer.html`。
