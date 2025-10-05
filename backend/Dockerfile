# syntax=docker/dockerfile:1

# Build the Spring Boot application using Gradle wrapper
FROM gradle:8.7-jdk21 AS build
WORKDIR /workspace

# Copy build configuration first for better caching
COPY build.gradle.kts settings.gradle.kts gradlew gradlew.bat /workspace/
COPY gradle /workspace/gradle
COPY backend/build.gradle.kts /workspace/backend/

# Download backend dependencies
RUN ./gradlew :backend:dependencies --no-daemon || true

# Copy source files
COPY . /workspace

# Build the executable jar for the backend module
RUN ./gradlew :backend:bootJar --no-daemon

# Runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
EXPOSE 8080

ENV JAVA_OPTS ""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
