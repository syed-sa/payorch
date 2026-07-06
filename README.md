# Payorch Platform

A multi-module Spring Boot payment orchestration platform for processing payments, handling webhooks, and reconciling provider state.

## Modules

- payorch-shared: shared domain model and payment-provider contract types
- payorch-providers: concrete provider adapters for Stripe, Razorpay, and the demo/mock provider
- core-orchestrator: main orchestration service for payment flows
- webhook-worker: webhook ingestion and forwarding service
- reconciliation-worker: reconciliation batch worker for provider status checks

## Requirements

- Java 21
- Maven or the Maven wrapper

## Build

From the repository root:

```bash
./mvnw -DskipTests compile
./mvnw clean install
```

## Run

```bash
./mvnw -pl core-orchestrator spring-boot:run
./mvnw -pl webhook-worker spring-boot:run
./mvnw -pl reconciliation-worker spring-boot:run
```

## Notes

- The project is wired as a Maven reactor from the repository root.
- Provider-facing contracts stay in the shared module, while concrete provider implementations live in the providers module.
- Configuration for database, provider credentials, and messaging should be supplied through each module's Spring configuration.
