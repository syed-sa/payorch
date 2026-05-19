# Multi-stage build for Webhook Worker Microservice

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml
COPY pom.xml .

# Download dependencies (this layer will be cached)
RUN mvn dependency:resolve

# Copy source code
COPY src src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create non-root user for security
RUN useradd -m -u 1000 appuser

# Copy JAR from builder
COPY --from=builder /app/target/webhook-worker-1.0.0.jar app.jar

# Change ownership
RUN chown appuser:appuser app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD java -cp /app/app.jar -Dserver.port=8081 \
    org.springframework.boot.loader.JarLauncher \
    --server.port=8081 > /dev/null 2>&1 || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
