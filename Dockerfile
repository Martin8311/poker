# syntax=docker/dockerfile:1

# ---------- 构建阶段：用 Maven 打出可执行 fat jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先拷贝 pom 并预下载依赖，利用 Docker 层缓存（仅源码变动时不必重新下载依赖）
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再拷贝源码并打包（跳过测试，加快镜像构建）
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段：仅含 JRE + jar，镜像更小 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
