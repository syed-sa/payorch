# Webhook Worker - Development Guide

This guide explains how to set up, build, and run the Webhook Worker microservice locally.

## Prerequisites

- Java 17 or higher
- Maven 3.9+
- Docker and Docker Compose (for Kafka)
- Git

## Quick Start

### 1. Setup Kafka (Docker)

Start Kafka and Zookeeper using Docker Compose:

```bash
# From the project root directory
docker-compose -f docker-compose.webhook.yml up -d
```

This starts:

- Kafka on `localhost:9092`
- Zookeeper on `localhost:2181`
- Kafka UI on `http://localhost:8080` (optional visualization)

### 2. Build the Application

```bash
cd webhook-worker
mvn clean package
```

### 3. Run the Application

#### Option A: Using Maven

```bash
mvn spring-boot:run
```

#### Option B: Using Java JAR

```bash
java -jar target/webhook-worker-1.0.0.jar
```

#### Option C: With Environment Variables

```bash
export STRIPE_WEBHOOK_SECRET=whsec_test_secret
export RAZORPAY_WEBHOOK_SECRET=razorpay_test_secret
mvn spring-boot:run
```

The service will start on `http://localhost:8081`

### 4. Verify the Service is Running

```bash
# Check Stripe webhook health
curl http://localhost:8081/webhooks/stripe/health

# Check Razorpay webhook health
curl http://localhost:8081/webhooks/razorpay/health
```

Expected response:

```json
{
  "status": "healthy",
  "service": "stripe-webhook"
}
```

## Configuration

### Environment Variables

Set these environment variables before starting the service:

| Variable                         | Description                  | Required                        |
| -------------------------------- | ---------------------------- | ------------------------------- |
| `STRIPE_WEBHOOK_SECRET`          | Your Stripe webhook secret   | Yes                             |
| `RAZORPAY_WEBHOOK_SECRET`        | Your Razorpay webhook secret | Yes                             |
| `STRIPE_API_KEY`                 | Your Stripe API key          | No                              |
| `RAZORPAY_KEY_ID`                | Your Razorpay key ID         | No                              |
| `RAZORPAY_KEY_SECRET`            | Your Razorpay key secret     | No                              |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers      | No (defaults to localhost:9092) |

### application.yml

Modify `src/main/resources/application.yml` to customize:

- Server port (default: 8081)
- Kafka topics (default: stripe.webhook.events, razorpay.webhook.events)
- Logging levels

## API Endpoints

### Stripe Webhooks

**POST** `/webhooks/stripe`

- Receives and processes Stripe webhook events
- Requires: `Stripe-Signature` header with event signature
- Response: `{"status": "received", "eventId": "evt_123"}`

**GET** `/webhooks/stripe/health`

- Health check endpoint
- Response: `{"status": "healthy", "service": "stripe-webhook"}`

### Razorpay Webhooks

**POST** `/webhooks/razorpay`

- Receives and processes Razorpay webhook events
- Requires: `X-Razorpay-Signature` header with event signature
- Response: `{"status": "received", "eventId": "evt_123"}`

**GET** `/webhooks/razorpay/health`

- Health check endpoint
- Response: `{"status": "healthy", "service": "razorpay-webhook"}`

## Testing Webhooks Locally

### Using cURL

```bash
# Test Stripe webhook
curl -X POST http://localhost:8081/webhooks/stripe \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: t=1614000000,v1=test_sig" \
  -d '{"id":"evt_test","type":"charge.succeeded","data":{"object":{"id":"ch_test"}}}'

# Test Razorpay webhook
curl -X POST http://localhost:8081/webhooks/razorpay \
  -H "Content-Type: application/json" \
  -H "X-Razorpay-Signature: test_signature" \
  -d '{"id":"evt_test","event":"order.paid"}'
```

### Using Postman

1. Import the webhook requests from `postman-collection.json`
2. Set environment variables for signatures
3. Send requests to test endpoints

## Monitoring

### View Logs

In development, logs are printed to console:

```bash
# Follow logs in real-time
mvn spring-boot:run | grep "payorch"

# Or check for specific events
mvn spring-boot:run | grep "webhook"
```

### Monitor Kafka Topics

```bash
# List all topics
docker exec payorch-kafka kafka-topics --list --bootstrap-server localhost:9092

# View messages in topic (Stripe)
docker exec payorch-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic stripe.webhook.events \
  --from-beginning

# View messages in topic (Razorpay)
docker exec payorch-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic razorpay.webhook.events \
  --from-beginning
```

### Kafka UI

Open `http://localhost:8080` to visualize:

- Topics
- Messages
- Consumers
- Brokers

## Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=StripeWebhookControllerTest

# Run with coverage
mvn test jacoco:report
```

## Building Docker Image

```bash
# Build the Docker image
docker build -t payorch-webhook-worker:latest .

# Run the container
docker run -p 8081:8081 \
  -e STRIPE_WEBHOOK_SECRET=your_secret \
  -e KAFKA_BROKERS=host.docker.internal:9092 \
  payorch-webhook-worker:latest
```

## Troubleshooting

### Kafka Connection Refused

```
Error: java.net.ConnectException: Connection refused
```

**Solution**: Ensure Kafka is running: `docker-compose -f docker-compose.webhook.yml ps`

### Webhook Signature Invalid

```
WARN: Invalid Stripe webhook signature
```

**Solution**: Verify the `STRIPE_WEBHOOK_SECRET` environment variable matches your Stripe configuration

### Port Already in Use

```
Error: Address already in use: bind
```

**Solution**: Change the port in `application.yml` or kill the process using port 8081:

```bash
lsof -i :8081  # Find process
kill -9 <PID>  # Kill process
```

### Topics Not Created

```
Topic 'stripe.webhook.events' does not exist
```

**Solution**: Topics are auto-created by Kafka. Ensure `auto.create.topics.enable=true` in Kafka config

## Integration with Main Orchestrator

The main Orchestrator (running on port 8080) subscribes to:

- `stripe.webhook.events`
- `razorpay.webhook.events`

Webhook events are automatically processed by the orchestrator for ledger and transaction updates.

## Best Practices

1. **Never hardcode secrets** - Always use environment variables
2. **Validate signatures** - Always verify webhook authenticity
3. **Fast response** - Publish to Kafka and immediately acknowledge
4. **Idempotent processing** - Use event IDs as Kafka keys for deduplication
5. **Monitor logging** - Check logs for validation failures
6. **Test endpoints** - Regularly test health checks in production

## IDE Setup

### IntelliJ IDEA

1. Open the project
2. Enable annotation processing: Settings → Compiler → Annotation Processors → Enable annotation processing
3. Install Lombok plugin: Settings → Plugins → Search "Lombok" → Install

### VS Code

1. Install Extension Pack for Java
2. Install Project Lombok extension
3. Install Spring Boot Extension Pack

## Next Steps

- [ ] Configure Stripe webhook secrets
- [ ] Configure Razorpay webhook secrets
- [ ] Deploy to Docker
- [ ] Set up monitoring and alerting
- [ ] Configure HTTPS for production
- [ ] Add rate limiting middleware
- [ ] Set up CI/CD pipeline

## Additional Resources

- [Stripe Webhooks Documentation](https://stripe.com/docs/webhooks)
- [Razorpay Webhooks Documentation](https://razorpay.com/docs/webhooks/)
- [Spring Kafka Documentation](https://spring.io/projects/spring-kafka)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
