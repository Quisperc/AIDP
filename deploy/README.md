# 服务器侧约定（流水线依赖）

> 本文件描述 **CI 发布链路假设服务器上已经存在的东西**。
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

> 健康状态里包含 Redis 探针：`SESSION_STORE=redis` 但 Redis 连不上时，服务本身也基本不可用，
> 此时发布判定失败是正确的。若某次确实要在无 Redis 的情况下发版，把
> `AUTH_HEALTH_URL` 指到 `/actuator/health/liveness`（只判进程存活）。

端口变了不用改 `.cnb.yml`：在 CNB 的 `server-config.yml` 里覆盖

```yaml
env:
  AUTH_HEALTH_URL: http://127.0.0.1:18090/actuator/health
```

## 三、容器内必须存在的环境变量

写在 `/opt/1panel/runtime/java/idp_auth_server/.env`（`run.sh` 会 source 它）。

```
APP_BASE_URL=https://idp.civer.cn      # 决定 OIDC issuer，配错会导致子系统验签失败
APP_BOOTSTRAP_SEED_DEMO_USERS=false    # 生产不要演示账号
SESSION_STORE=redis                    # 单机可省略
CACHE_TYPE=redis
REDIS_HOST=1Panel-redis-ghWF
REDIS_PASSWORD=<从 Redis 容器取>
```

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
git push origin :refs/tags/auth-v2.3.9        # 删掉已存在的标签
git tag auth-v2.3.9 <上一个标签的 commit>      # 重建（内容不变）
git push origin refs/tags/auth-v2.3.9         # 重新触发一次构建与发布
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
