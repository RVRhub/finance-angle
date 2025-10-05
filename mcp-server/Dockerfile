# syntax=docker/dockerfile:1

FROM gradle:8.7-jdk21 AS build
WORKDIR /workspace

COPY build.gradle.kts settings.gradle.kts gradlew gradlew.bat /workspace/
COPY gradle /workspace/gradle
COPY mcp-server/build.gradle.kts /workspace/mcp-server/

RUN ./gradlew :mcp-server:dependencies --no-daemon || true

COPY . /workspace

RUN ./gradlew :mcp-server:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/mcp-server/build/install/mcp-server/ ./

ENTRYPOINT ["./bin/mcp-server"]
