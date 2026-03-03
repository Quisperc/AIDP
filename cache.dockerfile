# 选择与你编译环境一致的基础镜像
FROM maven:3.9.12-eclipse-temurin-21

WORKDIR /build_cache

# 拷贝依赖定义文件
COPY pom.xml .
COPY client-usermanage/pom.xml client-usermanage/pom.xml

# 预下载所有依赖到镜像内的 /root/.m2
# go-offline 会下载所有插件和依赖，-B 是非交互模式
RUN mvn dependency:go-offline -B

# 也可以在此预先创建目录防止权限问题
RUN mkdir -p /root/.m2
