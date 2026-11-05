# 生产部署 SOP（jar / Docker）

> 面向第一次在一台干净服务器上把认证中心部署到「可交付」状态的人。
> 按顺序执行即可，每一步都有**验收命令**与**预期输出**；跳过任何一步都可能在后面变成难查的故障。
>
> 本文只讲「怎么装、怎么验、怎么退」。变量的完整含义与优先级见
> [`配置参考.md`](配置参考.md)，发布流水线与服务器约定见
> [`部署与运维.md`](部署与运维.md) 与 [`../deploy/README.md`](../deploy/README.md)。
>
> **部署单元只有一个 jar**：OIDC 提供方、管理台、登录/同意页都在里面。
> 外部依赖只有两个：PostgreSQL（必需）与 Redis（生产必需，会话与登录限流）。
> 证书与 HTTPS 由反向代理负责，应用本身只监听本机 HTTP。

---

## 零、准备清单

| 项 | 要求 | 验收 |
|---|---|---|
| JDK | 22 及以上（产物 `release=22`，25 亦可） | `java -version` |
| PostgreSQL | 14+（生产现为 18） | `psql -c 'select version()'` |
| Redis | 建议 6+，设密码；共享实例需带 namespace 隔离 | `redis-cli -a ... ping` → `PONG` |
| 反向代理 | Nginx / OpenResty / 1Panel，负责 TLS | 浏览器能打开 `https://<域名>` |
| 域名 | 一个固定域名，**上线后不要改** | `APP_BASE_URL` 与它逐字符一致 |
| 端口 | 应用 8080 只对本机；5432/6379 只对内网 | `ss -lntp` |

> **域名为什么不能改**：`issuer` 取 `APP_BASE_URL`，已签发 token 的 `iss` 与各子系统
> `issuer-uri` 都绑死它。换域名等于让所有子系统验签失败，必须同步改所有客户端配置。

---

## 一、一次性准备（数据库 / Redis / 目录）

### 1.1 建库建角色

```sql
CREATE ROLE aidp LOGIN PASSWORD '<强口令>';
CREATE DATABASE authdb OWNER aidp ENCODING 'UTF8';
```

验收：

```bash
psql "postgresql://aidp:<强口令>@127.0.0.1:5432/authdb" -c 'select 1'
```

> 表结构不用手工建：首次启动时 Flyway 自动执行 `V1 → V2 → V3`。
> **不要**再用 `schema.sql` 或 `ddl-auto=update`，两套机制并存会打架。

### 1.2 Redis 准备

```bash
redis-cli -h <host> -a '<口令>' CONFIG SET notify-keyspace-events "Egx"
redis-cli -h <host> -a '<口令>' CONFIG GET notify-keyspace-events   # 期望类似 gxE
```

- `Egx` 是 Spring Session 接收会话过期事件的前提；缺了它，键仍会按 TTL 过期，
  但应用侧收不到 expired 事件，依赖该事件的清理/统计逻辑不会触发。
  显式退出不受影响（登出会直接删键），所以这个坑通常只在会话自然过期时才显形。
- 应用侧配置是 `configure-action: none`，**不会**替你改这个全局设置；共享 Redis 请与其它应用协商。
- 应用键前缀固定为 `aidp:auth`，与其它应用天然隔离，不要改成公共前缀。

### 1.3 目录与运行账号

```bash
sudo useradd --system --home /opt/aidp --shell /usr/sbin/nologin aidp
sudo mkdir -p /opt/aidp/releases /etc/aidp
sudo chown -R aidp:aidp /opt/aidp
sudo install -m 600 /dev/null /etc/aidp/aidp.env
```

`releases/` 用来保留上一版 jar，回滚时不必重新构建。

---

## 二、构建产物

在**构建机**（不是服务器，服务器不必装 Maven）执行：

```bash
mvn clean package                     # 含 53 个回归测试，测试不过不会产出 jar
ls -lh target/*.jar                   # 取可执行 jar（忽略 *.jar.original）
sha256sum target/*.jar
```

上传并原子替换（避免覆盖运行中的 jar 触发 `ETXTBSY`）：

```bash
scp target/auth-server-*.jar <user>@<host>:/opt/aidp/releases/Auth-Server-$(date +%Y%m%d%H%M).jar
```

> 本地默认版本号是 `0.0.0-SNAPSHOT`；流水线会用标签里的 `X.Y.Z` 覆盖。
> 文件名不重要，**内容**才重要，建议按时间或版本归档。

---

## 三、路径 A：systemd + jar（裸机推荐）

### 3.1 放 jar 并建软链

```bash
cd /opt/aidp
sudo ln -sfn releases/Auth-Server-<版本或时间>.jar Auth-Server.jar
sudo chown -h aidp:aidp Auth-Server.jar
```

### 3.2 写环境变量 `/etc/aidp/aidp.env`

```dotenv
DB_URL=jdbc:postgresql://127.0.0.1:5432/authdb
DB_USERNAME=aidp
DB_PASSWORD=<强口令>
APP_BASE_URL=https://idp.example.com
CACHE_TYPE=redis
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=<Redis 口令>
APP_BOOTSTRAP_SEED_DEMO_USERS=false
APP_BOOTSTRAP_DEMO_USER_PASSWORD=<临时强口令，仅首启用>
```

要点：

- **建议统一不加引号**：systemd 的 `EnvironmentFile` 会剥掉引号，Docker 的 `--env-file` **不会**
  （引号会被当成值的一部分）。两条路径共用一份写法，换部署方式时才不会突然连不上库。
- 不要写 `export`。短名与 `SPRING_DATASOURCE_URL` 这类长名都能生效，但**同一份文件里不要混用**，
  否则排查时分不清哪一层在生效。
- 生产不要开 `SHOW_SQL`、不要开 `DDL_AUTO=update`。
- 权限必须是 `600`，属主 `aidp`。

### 3.3 `/etc/systemd/system/aidp.service`

```ini
[Unit]
Description=AIDP Auth Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=aidp
Group=aidp
WorkingDirectory=/opt/aidp
EnvironmentFile=/etc/aidp/aidp.env
# 只监听本机，公网入口交给反代；不要让 8080 直接暴露
ExecStart=/usr/bin/java -Xms512m -Xmx1g -Dfile.encoding=UTF-8 \
  -jar /opt/aidp/Auth-Server.jar --server.address=127.0.0.1
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now aidp
sudo systemctl status aidp --no-pager
```

### 3.4 首次启动会发生什么

看日志确认三件事，缺一不可：

```bash
sudo journalctl -u aidp -n 80 --no-pager
```

1. `[flyway] migrating from classpath:db/migration` → `Successfully applied 3 migrations`
2. `Started AuthServerApplication in ... seconds`
3. 若用户表为空，会出现：
   `[bootstrap] 用户表为空且 app.bootstrap.seed-demo-users=false：当前没有任何可登录账号`

**第 3 条是正常的**——空库不会自动造账号。先做下面的「首个管理员」，再去反代。

### 3.5 创建首个管理员（二选一）

**方式一（推荐，不需要额外工具）**：临时开启引导，首次启动会写入 `admin` 与 `user` 两个账号，
口令都取 `APP_BOOTSTRAP_DEMO_USER_PASSWORD`。

```bash
sudo sed -i 's/^APP_BOOTSTRAP_SEED_DEMO_USERS=.*/APP_BOOTSTRAP_SEED_DEMO_USERS=true/' /etc/aidp/aidp.env
sudo systemctl restart aidp
```

> 该逻辑**只在 `users` 表为空时执行一次**，之后重启不会重建、也不会覆盖已改的密码。
> 随后：用 `admin` 登录 → `/account` 改密 → 到 `/admin/users` 停用多余的 `user` →
> 把开关改回 `false` 并重启。

**方式二（能生成 BCrypt 时）**：直接插一条管理员，不经过演示账号。

```bash
# 任选一种生成 BCrypt（Spring 的 BCryptPasswordEncoder 接受 $2a$ / $2b$ / $2y$）
htpasswd -bnBC 10 "" '<强口令>' | tr -d ':\n'          # apache2-utils
# 或：docker run --rm httpd:alpine htpasswd -bnBC 10 "" '<强口令>' | tr -d ':\n'
```

```sql
INSERT INTO users (username, password, role, enabled, public_id, email_verified)
VALUES ('admin', '<上面生成的 $2a$... 串>', 'ROLE_ADMIN', true, gen_random_uuid()::text, false);
```

### 3.6 反向代理（Nginx 示例）

```nginx
server {
    listen 80;
    server_name idp.example.com;
    return 301 https://$host$request_uri;
}

server {
    # nginx ≥ 1.25 用下面两行；更老版本把这两行换成 listen 443 ssl http2;
    listen 443 ssl;
    http2 on;
    server_name idp.example.com;

    ssl_certificate     /etc/nginx/certs/idp.example.com.pem;
    ssl_certificate_key /etc/nginx/certs/idp.example.com.key;

    client_max_body_size 2m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        # 应用侧 forward-headers-strategy=native，靠这几个头还原对外地址；
        # 缺 X-Forwarded-Proto 会让 issuer / 跳转退回 http 或内网地址。
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-Host  $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port  $server_port;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;

        proxy_read_timeout 60s;
    }
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

> `APP_BASE_URL` 必须是 `https://idp.example.com`（与 `server_name` 一致）。
> 用 IP 或内网地址访问会得到 302 跳到错误域名，这不是故障，是配置不一致。

---

## 四、路径 B：Docker（与当前流水线一致）

仓库**不构建应用镜像**：`cache.dockerfile` 只用于 CI 依赖预热，`docker-compose.yml` 只起 PostgreSQL。
生产做法是「官方 JRE 镜像 + 挂载 jar」，与 1Panel 的部署形态相同。

### 4.1 目录与 env

```bash
sudo mkdir -p /opt/aidp-docker
# 把 jar 放到 /opt/aidp-docker/Auth-Server.jar
sudo install -m 600 /dev/null /opt/aidp-docker/aidp.env
```

`aidp.env` 内容同 3.2，但**同样不要加引号**——`docker --env-file` 不会剥离引号。

### 4.2 启动

```bash
docker run -d --name aidp \
  --restart unless-stopped \
  --env-file /opt/aidp-docker/aidp.env \
  -p 127.0.0.1:8080:8080 \
  -v /opt/aidp-docker/Auth-Server.jar:/app/Auth-Server.jar:ro \
  -w /app \
  eclipse-temurin:22-jre \
  java -Dfile.encoding=UTF-8 -jar Auth-Server.jar
```

```bash
docker logs -f aidp                                  # 看到 Started / Flyway 完成
curl -s http://127.0.0.1:8080/actuator/health        # 宿主机侧验活，最稳
```

> `eclipse-temurin` 的 JRE 镜像不保证自带 `curl`，所以这里不配 `--health-cmd`；
> 需要容器级 healthcheck 时，用自带 `curl` 的镜像（`-jdk` 或 4.3 的自建镜像）再加。
> 探活以宿主机 `curl` 与反代可用为准。

> 容器要访问**宿主机**的 PostgreSQL / Redis 时，别写 `127.0.0.1`（那是容器自己）。
> 用 `--add-host=host.docker.internal:host-gateway` 并把 `DB_URL` 指向
> `host.docker.internal`，或让容器加入数据库所在的 Docker 网络、用容器名互访。

### 4.3 可选：自建镜像

需要把 jar 固化进镜像时，用下面这个最小 Dockerfile（放在构建机，不进仓库也行）：

```dockerfile
FROM eclipse-temurin:22-jre
WORKDIR /app
COPY Auth-Server.jar /app/Auth-Server.jar
RUN useradd --system --uid 10001 app
USER app
EXPOSE 8080
ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-jar","/app/Auth-Server.jar"]
```

镜像不携带任何配置与密钥，全部由 `--env-file` 注入；这保证了同一镜像可在测试/生产复用。

---

## 五、验收（两条路径通用）

在**服务器本机**逐条执行：

```bash
# 1. 健康：匿名只返回状态；Redis/DB 任一不通这里就不是 UP
curl -s http://127.0.0.1:8080/actuator/health
# 期望：{"groups":["liveness","readiness"],"status":"UP"}

# 2. issuer 必须是公网 HTTPS 地址
curl -s https://<域名>/.well-known/openid-configuration | grep -o '"issuer":"[^"]*"'

# 3. 登录页可用且为 UTF-8
curl -s -o /dev/null -w '%{http_code} %{content_type}\n' https://<域名>/login
# 期望：200 text/html;charset=UTF-8

# 4. 管理台未登录被拦截（HTML 请求是 302 到 /login）
curl -s -H 'Accept: text/html' -o /dev/null -w '%{http_code} %{redirect_url}\n' https://<域名>/admin/users

# 5. 会话确实落在 Redis（访问一次 /login 后）
redis-cli -h <host> -a '<口令>' --scan --pattern 'aidp:auth:sessions:*' | head -3

# 6. 日志 0 条 ERROR
journalctl -u aidp --since '-5min' | grep -c ERROR        # 期望 0
# 容器：docker logs --since 5m aidp 2>&1 | grep -c ERROR
```

再走一遍浏览器闭环：登录 → `/admin/users` → 新建用户 → `/admin/clients` 注册客户端 →
`/account` 改资料 → 退出。**做到这一步才算「完完全全部署完成」**，前面的 curl 只证明进程活着。

---

## 六、升级与回滚

### 6.1 升级（每次发版）

```bash
# 1. 备份（有迁移时必做，见 6.3）
sudo mkdir -p /var/backups
pg_dump -Fc "postgresql://aidp:<口令>@127.0.0.1:5432/authdb" -f /var/backups/aidp-$(date +%F-%H%M).dump

# 2. 上传新 jar 到 releases/，切换软链（systemd）或替换挂载文件（Docker）
# 3. 重启：systemd 用 systemctl restart aidp；Docker 用 docker restart aidp
#    （compose 管理时用 docker compose up -d --force-recreate）
sudo systemctl restart aidp
# 4. 看 Flyway 与启动结果，再跑第五节验收
```

### 6.2 回滚

- **只改代码、没加迁移**：软链指回上一版 jar（或换回旧挂载文件）→ 重启 → 验收。
  两条路径都不需要动环境变量。
- **加了迁移**：Flyway 不回滚结构。必须「旧 jar + 升级前的数据库备份」一起回退：

```bash
sudo systemctl stop aidp
pg_restore --clean --if-exists -d "postgresql://aidp:<口令>@127.0.0.1:5432/authdb" /var/backups/aidp-<时间>.dump
sudo ln -sfn releases/<上一版>.jar /opt/aidp/Auth-Server.jar
sudo systemctl start aidp
```

> 反过来「只换旧 jar、不恢复数据库」会让 Flyway 报
> `Detected applied migration not resolved locally` 并拒绝启动——这是保护，不是 bug。

### 6.3 迁移前必读

`V3__account_profile_and_subject.sql` 是**破坏性**迁移：它会清空
`oauth2_authorization` 与 `oauth2_authorization_consent`，并把所有客户端 scope 规范为
`openid,profile,email`。**上线前必须备份**，且要有「所有用户需重新登录并重新同意」的预期。
已应用的迁移脚本一律只读：改一句注释都会因 checksum 校验失败而启动不了，改表只能新增 `V4`。

---

## 七、常见故障对照

| 现象 | 最可能原因 | 处理 |
|---|---|---|
| health `DOWN`，日志 `Unable to connect to localhost:6379` | 环境变量没进 Java 进程（`EnvironmentFile` 路径错、Docker `--env-file` 没带、1Panel 的 `run.sh` 少了 `set -a`） | 核对进程实际环境；systemd 用 `systemctl show aidp -p Environment` |
| health `DOWN`，但 Redis 正常 | 数据库连不上或口令错 | 看启动日志 `HikariPool` / `Flyway` 段 |
| 登录页 502（反代） | 应用没起或只监听 127.0.0.1 且反代指向了别处 | `ss -lntp \| grep 8080`，核对 `proxy_pass` |
| 跳转地址带内网 IP / `http://` | 反代缺 `X-Forwarded-*`，或 `APP_BASE_URL` 写错 | 补头、改 `APP_BASE_URL` 后重启 |
| 任何密码都不对，日志有「没有任何可登录账号」 | 空库且未做首个管理员 | 按 3.5 处理 |
| 重启后全员掉线 | 会话退回了本地 Servlet 会话（缺 session starter）或 Redis 被清 | 核对依赖与 `aidp:auth:sessions:*` |
| token 校验失败、`iss` 不匹配 | 域名/协议与 `APP_BASE_URL` 不一致，或子系统 `issuer-uri` 没同步 | 两边逐字符对齐 |
| `Detected applied migration not resolved locally` | 用旧 jar 跑了新库 | 按 6.2 恢复数据库备份 |

---

## 八、与其它文档的分工

| 你想要的 | 看哪份 |
|---|---|
| 每个变量的默认值、优先级、三种运行形态 | [`配置参考.md`](配置参考.md) |
| 流水线发版、1Panel 服务器约定、历史发布记录 | [`部署与运维.md`](部署与运维.md) |
| 换机器时必须存在的目录、容器名、端口、CI 密钥 | [`../deploy/README.md`](../deploy/README.md) |
| 子系统怎么接入、怎么登出 | [`接入规范.md`](接入规范.md) / [`外部系统接入.md`](外部系统接入.md) |
| 已经踩过的坑与根因 | [`故障复盘.md`](故障复盘.md) |
