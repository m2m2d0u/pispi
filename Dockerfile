# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S pispi && adduser -S pispi -G pispi

# Copy the built jar
COPY --from=builder /build/target/pispi-*.jar app.jar

# mTLS certificates (mounted as volume in production)
RUN mkdir -p /app/certs/client /app/certs/truststore
COPY --chown=pispi:pispi src/main/resources/client/ /app/certs/client/
COPY --chown=pispi:pispi src/main/resources/truststore/ /app/certs/truststore/

USER pispi

EXPOSE 8080 8081

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
