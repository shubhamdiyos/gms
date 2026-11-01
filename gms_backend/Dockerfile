# Multi-stage build for Spring Boot application
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Not root. The application can read exactly one file and write nothing it needs; a container
# that escapes for any reason should land in an account with no ownership of the host's uid 0.
# Created before the COPY, because --chown resolves the name in the layer it appears in.
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built JAR from build stage
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

USER spring:spring

# Expose port
EXPOSE 8080

# -XX:MaxRAMPercentage, because the container limit is the only memory figure that is true here:
# a JVM that does not read cgroups sizes its heap against the host and is OOM-killed by the kernel
# at a number the operator never chose. `java -jar` inherits that decision.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

