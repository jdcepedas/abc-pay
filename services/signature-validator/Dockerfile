# syntax=docker/dockerfile:1.6
FROM gradle:8.10-jdk21 AS build
USER root
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY shared shared
COPY services services
COPY tests tests
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :services:signature-validator:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/services/signature-validator/build/libs/signature-validator.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
