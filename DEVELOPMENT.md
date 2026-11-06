# AIDP · 开发与部署

## 环境要求
- JDK 22、Maven 3.9+
- Docker / Docker Compose（起依赖服务）
- MySQL、Redis（连接信息见 `application-secret.yml`）

## 配置
- `application-secret.yml` 是本机真实密钥文件，**不要提交、不要外传**。
- 新机器按 `docs/` 与 `deploy/` 下的模板生成同名文件后填入实际值。
- `docker-compose.yml` / `cache.dockerfile` 描述依赖服务与缓存镜像。

## 本地开发
```bash
mvn clean package
docker compose up -d        # 起 MySQL / Redis 等依赖
mvn spring-boot:run         # 具体启动参数见 README.md 与 deploy/README.md
```

## 部署
按 `deploy/README.md` 的部署章节执行；`docker-compose.yml` 与 `cache.dockerfile` 可直接用于容器化。
项目处于开发与试运行阶段，部署前先确认 `application-secret.yml` 已就位。

## 验证
`mvn clean package` 通过、服务启动后按 `docs/` 中的接口说明做一次认证链路自检。
