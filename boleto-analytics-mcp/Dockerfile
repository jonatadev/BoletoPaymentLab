FROM gradle:9.9.2-jdk25 AS build

WORKDIR /workspace
COPY . .
RUN ./gradlew :boleto-analytics-mcp:bootJar --no-daemon

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/boleto-analytics-mcp
COPY --from=build --chown=10001:10001 /workspace/boleto-analytics-mcp/build/libs/boleto-analytics-mcp-0.0.1-SNAPSHOT.jar app.jar
USER 10001

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/opt/boleto-analytics-mcp/app.jar"]
