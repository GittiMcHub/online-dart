FROM openjdk:17

RUN mkdir -p /app && \
    chown -R daemon /app

USER daemon
WORKDIR /app

COPY ./target/anthrax-1.0-SNAPSHOT-jar-with-dependencies.jar /app/anthrax.jar
COPY mqttbroker.conf /app/mqttbroker.conf

CMD ["java", "-jar", "/app/anthrax.jar"]
EXPOSE 1883