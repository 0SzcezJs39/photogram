FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# Запускает сборку: 
# -B - batch-режим (без интерактивных запросов, удобно для CI/логов),
# -DskipTests - не запускать тесты (им нужна живая MariaDB/S3, которых на этом этапе нет),
# package - собрать исполняемый jar в target/.
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS APP
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# Команда запуска контейнера в exec-форме (без shell): запускает Spring Boot jar. Так как это ENTRYPOINT, при docker run процесс сразу стартует приложение; свои аргументы можно добавлять после образа.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
