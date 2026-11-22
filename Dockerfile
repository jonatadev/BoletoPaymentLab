FROM gradle:9.9.1-jdk25 AS build

WORKDIR /workspace
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre

LABEL org.opencontainers.image.title="BoletoPaymentLab"

WORKDIR /opt/boleto-payment-lab
COPY --from=build --chown=10001:10001 /workspace/build/libs/BoletoPaymentLab-0.0.1-SNAPSHOT.jar app.jar
USER 10001

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/boleto-payment-lab/app.jar"]
