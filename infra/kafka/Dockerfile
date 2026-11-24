FROM apache/kafka:4.2.2 AS kafka
FROM gradle:9.9.1-jdk25 AS build
WORKDIR /src
COPY --from=kafka /opt/kafka/libs/ /kafka/
COPY KafkaSecurity.java KafkaSecurityChecks.java ./
RUN mkdir /classes && javac --release 17 -cp '/kafka/*' -d /classes KafkaSecurity.java KafkaSecurityChecks.java
FROM kafka
COPY --from=build /classes /opt/bpl-kafka/classes
ENTRYPOINT ["java", "-cp", "/opt/kafka/libs/*:/opt/bpl-kafka/classes"]
