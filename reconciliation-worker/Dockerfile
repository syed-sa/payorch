# File: [module-name]/Dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root application user for container security compliance
RUN addgroup -S payorch && adduser -S payorch -G payorch
USER payorch

# Copy the compiled executable JAR from your local target directory
COPY target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]