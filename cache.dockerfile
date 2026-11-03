# CI 依赖预热镜像
#
# 注意：这里刻意不使用 BuildKit 的 `--mount=type=cache`。
# 该语法把下载结果写进「构建缓存目录」，而不是镜像层 —— 构建结束后
# /root/.m2 依然是空的，产出的缓存镜像只是个空壳，后续 stage 仍要全量下载依赖
# （这是原实现的实际行为）。改成普通的 RUN，让依赖真正落进镜像层，
# 再由 Docker 的层缓存决定是否跳过：pom 不变就不会重新下载。
FROM maven:3.9.12-eclipse-temurin-25

WORKDIR /build_cache

# 先只拷贝依赖定义文件，保证「改代码」不会让这一层失效
COPY pom.xml .

RUN mvn -B -q dependency:go-offline
