# Payorch Webhook Worker Microservice

A lightweight, standalone Spring Boot application designed to receive, validate, and relay webhook events from payment providers (Stripe and Razorpay) to Kafka for asynchronous processing.

## Architecture

```
Payment Provider → Webhook → Webhook Worker (Port 8081)
                               ↓
                           Validate Signature
                               ↓
                         Push Raw Payload → Kafka
                               ↓
                           Acknowledge Receipt
```

## Key Features

✅ **Lightweight** - No database connections, minimal dependencies  
✅ **Fast** - Instantly validates and publishes to Kafka  
✅ **Reliable** - Idempotent event processing with Kafka exactly-once semantics  
✅ **Secure** - Cryptographic signature verification (HMAC-SHA256)  
✅ **Separated** - Runs on port 8081, isolated from main orchestrator (8080)

## Endpoints

### Stripe Webhooks

- **POST** `/webhooks/stripe` - Receive Stripe webhook events
- **GET** `/webhooks/stripe/health` - Health check

### Razorpay Webhooks

- **POST** `/webhooks/razorpay` - Receive Razorpay webhook events
- **GET** `/webhooks/razorpay/health` - Health check

## Configuration

### Environment Variables

```bash
# Stripe Configuration
export STRIPE_WEBHOOK_SECRET=whsec_test_...
export STRIPE_API_KEY=sk_test_...

# Razorpay Configuration
export RAZORPAY_WEBHOOK_SECRET=secret_...
export RAZORPAY_KEY_ID=rzp_test_...
export RAZORPAY_KEY_SECRET=secret_...
```

### Kafka Configuration

Ensure Kafka is running on `localhost:9092` (configurable in `application.yml`):

```bash
# Start Kafka (Docker)
docker run -d \
  -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -p 9092:9092 \
  confluentinc/cp-kafka:latest
```

## Building & Running

### Build the application

```bash
cd webhook-worker
mvn clean package
```

### Run the application

```bash
# Using Maven
mvn spring-boot:run

# Or using JAR
java -jar target/webhook-worker-1.0.0.jar
```

### Run with environment variables

```bash
export STRIPE_WEBHOOK_SECRET=your_secret
export RAZORPAY_WEBHOOK_SECRET=your_secret
mvn spring-boot:run
```

## Kafka Topics

The service publishes to two topics:

- `stripe.webhook.events` - Stripe webhook payloads
- `razorpay.webhook.events` - Razorpay webhook payloads

## Request/Response Examples

### Stripe Webhook Request

```bash
curl -X POST http://localhost:8081/webhooks/stripe \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: t=1614000000,v1=abcd1234..." \
  -d '{"id":"evt_1234","type":"charge.succeeded",...}'
```

### Razorpay Webhook Request

```bash
curl -X POST http://localhost:8081/webhooks/razorpay \
  -H "Content-Type: application/json" \
  -H "X-Razorpay-Signature: e57a1fe7c9abc..." \
  -d '{"id":"evt_1234","event":"order.paid",...}'
```

### Success Response

```json
{
  "status": "received",
  "eventId": "evt_1234"
}
```

### Error Response

```json
{
  "error": "Invalid signature"
}
```

## Security Considerations

1. **Signature Validation** - All webhooks must pass cryptographic signature verification
2. **Environment Variables** - Secrets are loaded from environment, not hardcoded
3. **HTTPS Only** - In production, configure Nginx/reverse proxy for HTTPS
4. **Rate Limiting** - Consider adding rate limiting middleware in production
5. **Idempotency** - Kafka exactly-once semantics prevent duplicate processing

## Monitoring & Logging

Log output is sent to console with format:

```
2024-05-19 14:30:45 [main] INFO com.payorch.webhook - Published stripe webhook event to topic: stripe.webhook.events
```

Configure logging in `application.yml`:

```yaml
logging:
  level:
    com.payorch: DEBUG
    org.springframework: INFO
```

## Integration with Main Orchestrator

The main orchestrator (Port 8080) subscribes to the Kafka topics:

- `stripe.webhook.events`
- `razorpay.webhook.events`

The orchestrator processes these events asynchronously, updating ledger entries and transaction statuses.

## Testing

Run tests with:

```bash
mvn test
```

## Deployment

### Docker Deployment

See [Dockerfile](../Dockerfile) for containerization.

```bash
docker build -t payorch-webhook-worker:latest .
docker run -p 8081:8081 \
  -e STRIPE_WEBHOOK_SECRET=your_secret \
  -e KAFKA_BROKERS=kafka:9092 \
  payorch-webhook-worker:latest
```

### Kubernetes Deployment

See [k8s-deployment.yaml](../k8s-webhook-worker.yaml)

```bash
kubectl apply -f k8s-webhook-worker.yaml
```

## Troubleshooting

**Webhook signature validation fails:**

- Verify the webhook secret matches exactly
- Check that the raw request body is preserved (not parsed JSON)

**Kafka connection fails:**

- Ensure Kafka is running and accessible
- Check `bootstrap-servers` in `application.yml`

**Events not appearing in topic:**

- Check application logs for errors
- Verify Kafka topic exists: `kafka-topics --list --bootstrap-server localhost:9092`

## Dependencies

- Spring Boot 3.0.0
- Spring Kafka
- Stripe SDK
- Razorpay SDK
- Apache Commons Codec
- Java 17+
