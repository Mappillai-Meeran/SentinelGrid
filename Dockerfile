# Multi-stage Dockerfile for SentinelGrid Spring Boot Backend
# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
EXPOSE 8080

COPY --from=build /app/target/sentinelgrid-1.0.0-SNAPSHOT.jar app.jar

# Render Free 512 MB memory constraint: container-aware heap allocation and exit on OOM
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod,demo

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
