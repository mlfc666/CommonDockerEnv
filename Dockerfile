FROM eclipse-temurin:17-jre-alpine

# 安装运行时必要的组件
RUN apk add --no-cache ttyd tmux coreutils

WORKDIR /app

# 这里的路径是相对于构建上下文（context）根目录的
# 我们会在 Workflow 里把 jar 包放到根目录，所以这里直接写 app.jar
COPY app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 13232

ENTRYPOINT ["java", "-jar", "app.jar"]