FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache ttyd tmux coreutils docker-cli

WORKDIR /app

# 这里的 app.jar 是 Workflow 搬运过来的
COPY app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 13232

ENTRYPOINT ["java", "-jar", "app.jar"]