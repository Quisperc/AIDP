# 服务器侧约定（流水线依赖）

> 本文件描述 **CI 发布链路假设服务器上已经存在的东西**。
> 从零搭建一台生产机的完整操作步骤见 [`../docs/生产部署SOP.md`](../docs/生产部署SOP.md)；
> 本文件只负责「流水线会碰到的那些路径与变量」。
> 这些文件不放在仓库里（含真实密钥与 1Panel 运行时配置），
> 但流水线强依赖它们，换机器/重装时必须逐项核对，否则发布会在最后一步失败。

## 一、目录结构

```
/home/idp/auth_server/Auth-Server.jar          # 流水线 rsync 的落地位置（挂载进容器）
/opt/1panel/runtime/java/idp_auth_server/      # 1Panel「Java 运行环境」，含 docker-compose.yml 与 .env
```

- rsync 只覆盖 jar，**不动** compose 与 .env。改配置请直接改服务器上的文件。
- `docker compose up -d --force-recreate` 在 `/opt/1panel/runtime/java/idp_auth_server` 下执行，
  因此该目录必须存在 compose 文件，且 **service 名称要与容器名一致**（`idp_auth_server`）。

> 旧的 `idp_user_manage` 容器与 `um.civer.cn` 在管理台并入认证中心后已不再需要，
> 下线步骤见 `docs/部署与运维.md` 第八节。

## 二、端口与健康检查

| 服务 | 容器名 | 宿主机端口 | 健康检查地址 |
|---|---|---|---|
| 认证中心 + 管理台 | `idp_auth_server` | `127.0.0.1:18090` → 8080 | `http://127.0.0.1:18090/actuator/health` |

流水线在 `docker compose up` 之后会轮询健康地址（40 次 × 3 秒），拿不到 `"UP"` 就判定发布失败
并打印最后 120 行容器日志。

探活成功后会在 `/home/idp/auth_server/.last-health-ok` 写一个时间戳，
用来区分「发布成功」和「发布成功但探活没跑」：

```bash
cat /home/idp/auth_server/.last-health-ok     # 例：2026-11-04T00:03:53+08:00
docker inspect -f '{{.State.StartedAt}}' idp_auth_server
```

两个时间相差 10 秒左右即为正常（容器启动 + Spring 起来 + 探活）。
健康检查脚本刻意写成单行、不带 shell 变量：这一段由 CNB 通过 SSH 在服务器上执行，
跨行变量会被替换成空串，曾导致空 URL 轮询 120 秒后误判失败。

> Redis 是会话与缓存的实际依赖：连不上时会话无法保存，服务基本不可用，
> 发布判定失败是正确的。Spring Boot 4 起会话由
> `spring-boot-starter-session-data-redis` 自动启用，不再有 `SESSION_STORE` 开关。

> **端口改动要动两处**：`.cnb.yml` 的健康校验把 `http://127.0.0.1:18090/actuator/health`
> 直接写在 curl 命令里（单行、无变量），改端口必须同步改这一行；只改环境变量不起作用。

## 三、容器内必须存在的环境变量

写在 `/opt/1panel/runtime/java/idp_auth_server/.env`（`run.sh` 会 source 它）。
**这是唯一的配置来源**：应用不引入任何自定义外部配置（`spring.config.import` 已于
2026-11-04 移除，`/home/idp/auth_server/application-secret.yml` 已改名为
`.disabled-*`）。注意工作目录下的 `application.yml` 仍会被 Boot 默认加载，别放。
历史上正是那个外部文件的优先级高于 jar 内配置、且盖过 `DB_URL`
环境变量，静默改写了数据源，才导致那次故障。
完整变量表见 `docs/配置参考.md`。

```
DB_URL="jdbc:postgresql://1Panel-postgresql-fViT:5432/idp_auth_db"
DB_USERNAME="idp_auth_db"
DB_PASSWORD="<强口令>"
APP_BASE_URL="https://idp.civer.cn"    # 决定 OIDC issuer，配错会导致子系统验签失败
APP_BOOTSTRAP_SEED_DEMO_USERS="false"  # 生产不要演示账号
CACHE_TYPE="redis"                     # 登录失败计数跨实例共享；会话随 starter 自动启用，无开关
REDIS_HOST="1Panel-redis-ghWF"
REDIS_PASSWORD="<从 Redis 容器取>"
```

> ⚠️ **1Panel 的坑（2026-11-04 实际踩过）**：面板生成的变量形如 `KEY="value"`，**不带 `export`**。
> 普通 shell 赋值不会传给子进程，`java -jar` 一个环境变量都读不到 —— 现象是 Redis 回落
> `localhost:6379` 连不上、`/actuator/health` 变 DOWN，而数据库/issuer 靠旧的外部 secret 文件兜着。
> 因此本目录的 `run.sh` 必须用 `set -a` 包裹 `source`：
>
> ```bash
> #!/bin/bash
> set -a
> source /.env
> set +a
> eval $EXEC_SCRIPT
> ```
>
> 升级 Java 版本或在面板里编辑运行环境后，1Panel 可能重写 `.env` / `run.sh`，
> **改完必须复核** `docker exec idp_auth_server sh -c 'tr "\0" "\n" < /proc/<java-pid>/environ'`
> 里是否还有 `DB_*` / `REDIS_*` / `APP_BASE_URL`，再用 `/actuator/health` 确认。

> `INITIAL_CLIENT_SECRET` 已废弃：客户端不再由代码预置，全部在管理台注册。

## 四、发布前核对清单

- [ ] 服务器上 `/home/idp/auth_server` 存在且可写
- [ ] `/opt/1panel/runtime/java/idp_auth_server` 下有可用的 `docker-compose.yml`
- [ ] `.env` 里 `APP_BASE_URL` 指向**对外**地址（不是内网 IP）
- [ ] 容器健康检查通过：`curl -s http://127.0.0.1:18090/actuator/health`
- [ ] CNB 项目已配置 `MAVEN_USER` / `MAVEN_TOKEN` / `SERVER_USER` / `SERVER_KEY` / `SERVER_HOST`
- [ ] GitHub 仓库已配置 `GIT_PASSWORD`（CNB 访问令牌）

## 五、回滚

流水线只做「覆盖 jar + 重启」，**不会自动保留上一版 jar**。回滚有两条路：

**A. 重新发布上一个标签（推荐，可追溯）**

```bash
git push origin :refs/tags/auth-vX.Y.Z        # 删掉已存在的标签（换成上一个版本号）
git tag auth-vX.Y.Z <上一个标签的 commit>      # 重建（内容不变）
git push origin refs/tags/auth-vX.Y.Z         # 重新触发一次构建与发布
```

或在 CNB 流水线历史里对上一个标签的构建点「重跑」。

**B. 手工替换 jar**

```bash
# 从 CNB Maven 制品库下载对应版本的 jar，然后：
cp <下载的jar> /home/idp/auth_server/Auth-Server.jar
cd /opt/1panel/runtime/java/idp_auth_server && docker compose up -d --force-recreate
```

> 已知改进项：让流水线保留 `releases/<version>/` 并切换软链，可实现真正的「一键回滚 + 自动回滚」。
> 需要先调整服务器上 compose 的挂载路径，属于运维改动，暂未纳入。
