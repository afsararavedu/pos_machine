# One Dockerfile for every service. Build with:
#   docker build --build-arg MODULE=payment-service -t pos/payment-service .
# Stage 1 compiles the whole reactor once (common + the chosen module).
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY issuer-simulator/pom.xml issuer-simulator/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY reports-service/pom.xml reports-service/pom.xml
# Cache dependencies in their own layer.
RUN mvn -q -B -pl ${MODULE} -am dependency:go-offline -DskipTests || true
COPY . .
RUN mvn -q -B -pl ${MODULE} -am package -DskipTests

# Stage 2: small JRE image, non-root.
FROM eclipse-temurin:21-jre-alpine
ARG MODULE
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}.jar app.jar
USER 10001
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Ports: auth 8081, payment 8082, issuer 8083, reports 8084
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
