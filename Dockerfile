# syntax=docker/dockerfile:1.6

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies first
COPY pom.xml ./
RUN mvn -B -q -e -DskipTests dependency:go-offline

# Build the executable jar
COPY src ./src
RUN mvn -B -q -e -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Non-root user
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /pdfs /reports \
    && chown -R app:app /app /pdfs /reports

COPY --from=build /build/target/pdf-validator-*.jar /app/app.jar

USER app

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]