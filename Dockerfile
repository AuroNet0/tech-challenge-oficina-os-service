FROM eclipse-temurin:21-jdk-alpine@sha256:b78a1d385da875588e21207f07dddf9a1418849971ae857fb24c8f3a7d441a72 AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp -DskipTests clean package && \
    test "$(find target -maxdepth 1 -name '*.jar' | wc -l)" -eq 1 && \
    cp target/*.jar /workspace/application.jar

FROM eclipse-temurin:21-jre-alpine@sha256:82cf3d9ccbe1ed46252008d51b2f2329e6b9e8a2f4394c4bebefc3ea7f4b1372
WORKDIR /app

RUN addgroup -g 10001 app && adduser -D -H -u 10001 -G app app
COPY --from=build --chown=10001:10001 /workspace/application.jar /app/app.jar
USER 10001:10001

EXPOSE 8080
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=5s --timeout=3s --start-period=60s --retries=12 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
