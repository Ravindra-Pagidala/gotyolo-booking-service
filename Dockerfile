FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y postgresql-client tini && rm -rf /var/lib/apt/lists/*

RUN groupadd -g 1001 appgroup && useradd -u 1001 -g appgroup appuser

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN mkdir -p /app/logs && \
    chown -R appuser:appgroup /app && \
    chmod -R 755 /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD pg_isready -h db -p 5432 -U postgres -d gotyolo || exit 1

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
