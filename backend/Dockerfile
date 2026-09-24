# ---------------------------------------------------------------------
# Build stage: compile and package the Spring Boot application.
# Tests run in CI; the image build only packages.
# ---------------------------------------------------------------------
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---------------------------------------------------------------------
# Runtime stage: JRE only, non root user, no build tools.
# ---------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S mrms && adduser -S -G mrms -H -s /sbin/nologin mrms \
    && mkdir -p /app /data/storage && chown -R mrms:mrms /data
WORKDIR /app
COPY --from=build /src/target/mrms-backend-*.jar /app/app.jar
USER mrms
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
