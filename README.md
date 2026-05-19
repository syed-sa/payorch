# Payorch Platform

A multi-module Spring Boot payment orchestration platform with separate microservices for payment processing and webhook handling.

## 📁 Project Structure

```
payorch-platform (Root Directory)
├── pom.xml                              ← Root Parent POM (manages both modules)
├── docker-compose.yml
├── docker-compose.webhook.yml
├── README.md
├── HELP.md
│
├── core-orchestrator                    ← Main payment orchestration service
│   ├── pom.xml                         (Child POM - inherits from root)
│   ├── README.md
│   ├── Dockerfile
│   ├── src/
│   │   ├── main/java/com/payorch/
│   │   │   ├── PayorchApplication.java
│   │   │   ├── common/
│   │   │   ├── ledger/
│   │   │   ├── orchestrator/
│   │   │   ├── outbox/
│   │   │   ├── providers/
│   │   │   └── ...
│   │   ├── main/resources/
│   │   │   ├── application.yml
│   │   │   ├── application.properties
│   │   │   └── db/migration/
│   │   └── test/
│   └── target/
│
└── webhook-worker                       ← Lightweight webhook receiver service
    ├── pom.xml                         (Child POM - inherits from root)
    ├── README.md
    ├── DEVELOPMENT.md
    ├── Dockerfile
    ├── .env.example
    ├── src/
    │   ├── main/java/com/payorch/webhook/
    │   │   ├── WebhookWorkerApplication.java
    │   │   ├── config/
    │   │   ├── controller/
    │   │   ├── producer/
    │   │   └── validator/
    │   ├── main/resources/
    │   │   └── application.yml
    │   └── test/
    └── target/
```

## 🏗️ Architecture

### Core Orchestrator (Port 8080)

- **Purpose**: Main payment orchestration service
- **Responsibilities**:
  - Ledger management
  - Payment transaction processing
  - Integration with payment providers (Stripe, Razorpay)
  - Database operations
  - Business logic
- **Database**: PostgreSQL
- **Technologies**: Spring Boot, Spring Data JPA, Flyway migrations

### Webhook Worker (Port 8081)

- **Purpose**: Lightweight webhook receiver and Kafka producer
- **Responsibilities**:
  - Receive webhooks from payment providers
  - Validate signatures
  - Publish to Kafka
  - Immediate acknowledgment
- **Database**: None (stateless)
- **Technologies**: Spring Boot, Spring Kafka

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 14+
- Docker & Docker Compose
- Git

### 1. Build the Entire Platform

```bash
# From the root directory
mvn clean install
```

This builds both modules:

- `core-orchestrator-0.0.1-SNAPSHOT.jar`
- `webhook-worker-0.0.1-SNAPSHOT.jar`

### 2. Run Individual Modules

#### Core Orchestrator

```bash
cd core-orchestrator
mvn spring-boot:run
# Runs on http://localhost:8080
```

#### Webhook Worker

```bash
cd webhook-worker
mvn spring-boot:run
# Runs on http://localhost:8081
```

### 3. Docker Compose

Start all services (Kafka, Orchestrator, Webhook Worker):

```bash
# Full stack with Kafka
docker-compose -f docker-compose.webhook.yml up -d

# Just webhook worker + Kafka
docker-compose -f docker-compose.webhook.yml up -d kafka zookeeper webhook-worker
```

## 📦 Shared Dependencies

All shared dependencies are managed by the **root parent POM** (`pom.xml`):

| Dependency        | Version | Used By           |
| ----------------- | ------- | ----------------- |
| Spring Boot       | 3.5.14  | Both              |
| Spring Kafka      | 3.1.5   | Both              |
| Stripe SDK        | 25.10.0 | Both              |
| Razorpay SDK      | 1.4.3   | Both              |
| PostgreSQL Driver | Latest  | Core Orchestrator |
| Flyway            | Latest  | Core Orchestrator |
| Resilience4j      | 2.2.0   | Core Orchestrator |
| Lombok            | Latest  | Both              |

## 🔧 Maven Commands

### Build All Modules

```bash
mvn clean install
```

### Build Specific Module

```bash
mvn clean install -pl core-orchestrator
mvn clean install -pl webhook-worker
```

### Run Tests

```bash
# All tests
mvn test

# Specific module
mvn test -pl webhook-worker
```

### Build Docker Images

```bash
# Build both
mvn clean package docker:build

# Core orchestrator
cd core-orchestrator
mvn clean package docker:build

# Webhook worker
cd webhook-worker
mvn clean package docker:build
```

## 🔌 API Endpoints

### Core Orchestrator (Port 8080)

- `POST /api/payments` - Initiate payment
- `GET /api/payments/{id}` - Get payment status
- `GET /api/accounts` - List accounts
- `POST /api/ledger/entries` - Record ledger entry

### Webhook Worker (Port 8081)

- `POST /webhooks/stripe` - Receive Stripe webhooks
- `GET /webhooks/stripe/health` - Stripe health check
- `POST /webhooks/razorpay` - Receive Razorpay webhooks
- `GET /webhooks/razorpay/health` - Razorpay health check

## 📊 Integration Flow

```
Payment Provider (Stripe/Razorpay)
         ↓
    Webhook Worker (Port 8081)
         ↓
    Validate Signature
         ↓
    Kafka Topic
         ↓
    Core Orchestrator (Port 8080) - Consumer
         ↓
    Update Ledger & Transactions
         ↓
    PostgreSQL Database
```

## 🧪 Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
mvn test -Dgroups=integration
```

### Test Coverage

```bash
mvn clean test jacoco:report
```

## 🐳 Docker Deployment

### Build Docker Images

```bash
# Build individual modules
cd core-orchestrator && mvn clean package docker:build
cd webhook-worker && mvn clean package docker:build
```

### Run with Docker Compose

```bash
docker-compose -f docker-compose.webhook.yml up
```

### Run Standalone

```bash
# Orchestrator
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/payorch \
  -e SPRING_DATASOURCE_USERNAME=payorch \
  -e SPRING_DATASOURCE_PASSWORD=password \
  payorch-core-orchestrator:0.0.1-SNAPSHOT

# Webhook Worker
docker run -p 8081:8081 \
  -e STRIPE_WEBHOOK_SECRET=your_secret \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  payorch-webhook-worker:0.0.1-SNAPSHOT
```

## 📚 Module Documentation

- [Core Orchestrator README](./core-orchestrator/README.md)
- [Webhook Worker README](./webhook-worker/README.md)
- [Webhook Worker Development Guide](./webhook-worker/DEVELOPMENT.md)

## 🔐 Environment Configuration

### Core Orchestrator

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/payorch
SPRING_DATASOURCE_USERNAME=payorch
SPRING_DATASOURCE_PASSWORD=password
STRIPE_API_KEY=sk_test_...
RAZORPAY_KEY_ID=rzp_test_...
KAFKA_BROKERS=localhost:9092
```

### Webhook Worker

```bash
STRIPE_WEBHOOK_SECRET=whsec_test_...
RAZORPAY_WEBHOOK_SECRET=razorpay_secret_...
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

## 🚨 Troubleshooting

### Kafka Connection Refused

```bash
# Ensure Kafka is running
docker-compose -f docker-compose.webhook.yml ps

# Restart if needed
docker-compose -f docker-compose.webhook.yml restart kafka
```

### Database Connection Failed

```bash
# Verify PostgreSQL is running and accessible
psql -h localhost -U payorch -d payorch

# Check connection string in application.yml
```

### Module Build Errors

```bash
# Clean build cache
mvn clean

# Verify parent POM can be resolved
mvn help:describe -Dproject=com.payorch:payorch-platform:0.0.1-SNAPSHOT
```

## 🔄 Multi-Module Build Process

1. **Parent POM** defines:
   - Version: `0.0.1-SNAPSHOT`
   - Java: 21
   - Spring Boot: 3.5.14
   - Dependency versions
   - Plugin management

2. **Child POMs** inherit from parent and define:
   - Their own artifact IDs
   - Module-specific dependencies
   - Module-specific configurations

3. **Build Order**:
   - Parent POM validation
   - Core Orchestrator module
   - Webhook Worker module
   - Integration tests (if enabled)

## 📈 Scaling Considerations

- **Webhook Worker**: Scale independently for high webhook volume
- **Core Orchestrator**: Manage database connections carefully when scaling
- **Kafka**: Ensure sufficient topics and partitions for both services

## 🤝 Contributing

1. Create a feature branch
2. Make changes in the appropriate module
3. Run tests: `mvn test`
4. Build: `mvn clean install`
5. Create a pull request

## 📝 License

[Add your license here]

## 🆘 Support

For issues or questions:

1. Check module-specific README files
2. Review DEVELOPMENT.md for setup guidance
3. Check Docker logs: `docker-compose logs -f webhook-worker`
