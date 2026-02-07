FROM eclipse-temurin:17-jre-alpine

# 安装 ttyd, tmux 和 coreutils (stty)
RUN apk add --no-cache ttyd tmux coreutils

WORKDIR /app

# 直接拷贝根目录下的 app.jar
COPY app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 13232

ENTRYPOINT ["java", "-jar", "app.jar"]