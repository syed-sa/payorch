# PayOrch Payment Platform - Complete Architecture & Flow Documentation

**Version:** 0.0.1-SNAPSHOT  
**Last Updated:** 2026-06-18  
**Purpose:** Enterprise payment orchestration platform with distributed ledger, multi-provider failover, and real-time reconciliation

---

## Table of Contents

1. [System Architecture Overview](#system-architecture-overview)
2. [Module Structure](#module-structure)
3. [Technology Stack](#technology-stack)
4. [Database Schema](#database-schema)
5. [Request Flow: Checkout to Settlement](#request-flow-checkout-to-settlement)
6. [Idempotency Mechanism](#idempotency-mechanism)
7. [Resilience & Circuit Breakers](#resilience--circuit-breakers)
8. [Event-Driven Architecture (Outbox Pattern)](#event-driven-architecture-outbox-pattern)
9. [Reconciliation Worker](#reconciliation-worker)
10. [Configuration & Environment](#configuration--environment)
11. [Key Algorithms & Patterns](#key-algorithms--patterns)
12. [Common Development Tasks](#common-development-tasks)

---

## System Architecture Overview
### System Architecture

- ✅ 4 microservices (core-orchestrator, webhook-worker, reconciliation-worker, payorch-shared)
- ✅ 8 technologies (Java 21, Spring Boot, PostgreSQL, Redis, Kafka, etc.)
- ✅ 6 database tables (all explained with relationships)
- ✅ Complete request flow with timing
- ✅ All algorithms (routing, locking, failover, reconciliation)

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                                │
│                                                                       │
│  Mobile App / Web Browser / REST Client                             │
└──────────────────────────┬──────────────────────────────────────────┘
                          │ HTTP REST (idempotency key header)
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│               CORE ORCHESTRATOR (Port: 8080)                         │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ CheckoutController (Entry point)                            │   │
│  │ - Validates: idempotency key, amount, currency             │   │
│  │ - Creates Transaction entity                                │   │
│  │ - Delegates to PaymentOrchestrator                          │   │
│  └──────────────────────┬──────────────────────────────────────┘   │
│                        │                                             │
│  ┌──────────────────────▼──────────────────────────────────────┐   │
│  │ PaymentOrchestrator (Core Logic)                            │   │
│  │                                                              │   │
│  │ Step 1: IdempotencyManager.getResponse(key)                │   │
│  │         ├─ L1: Check Redis cache (fast path)               │   │
│  │         └─ L2: Check Database (recovery path)              │   │
│  │         └─ Return cached response if found (replay)        │   │
│  │                                                              │   │
│  │ Step 2: IdempotencyManager.acquireLock(key)                │   │
│  │         └─ Distributed lock (Redis, 5-min auto-expire)     │   │
│  │         └─ Prevent concurrent execution (exactly-once)     │   │
│  │                                                              │   │
│  │ Step 3: SmartRoutingStrategy.selectBestProvider()          │   │
│  │         ├─ Score providers by: success rate, latency, cost │   │
│  │         ├─ Exclude failed providers from current retry     │   │
│  │         └─ Return optimal provider (STRIPE, RAZORPAY, etc) │   │
│  │                                                              │   │
│  │ Step 4: executeWithFailover() [Recursive]                  │   │
│  │         ├─ PaymentStateManager.initializePaymentState()   │   │
│  │         │  └─ Store intent to DB (INITIATED)              │   │
│  │         │                                                    │   │
│  │         ├─ CircuitBreaker.executeSupplier()                │   │
│  │         │  ├─ Resilience4j circuit breaker wraps call     │   │
│  │         │  ├─ 3 states: CLOSED (normal), OPEN (failing),   │   │
│  │         │  │           HALF_OPEN (testing recovery)        │   │
│  │         │  └─ Call provider.process(transaction)           │   │
│  │         │                                                    │   │
│  │         ├─ PaymentStateManager.finalizePaymentState()     │   │
│  │         │  ├─ Update transaction status (SUCCESS/FAILED)   │   │
│  │         │  ├─ Store provider reference ID                  │   │
│  │         │  └─ Mark for outbox event                        │   │
│  │         │                                                    │   │
│  │         └─ If provider fails → RECURSIVE FAILOVER:         │   │
│  │            Repeat steps 3-4 with next best provider        │   │
│  │            (until all providers exhausted)                 │   │
│  │                                                              │   │
│  │ Step 5: IdempotencyManager.saveResponse()                  │   │
│  │         ├─ Write to PostgreSQL (durable)                   │   │
│  │         ├─ Update Redis cache (performance)                │   │
│  │         └─ Release distributed lock                        │   │
│  │                                                              │   │
│  │ Return: ProviderResponse to client                          │   │
│  └────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ LedgerService (Double-Entry Accounting)                      │   │
│  │                                                               │   │
│  │ Triggered by: PaymentOrchestrator after transaction settled  │   │
│  │                                                               │   │
│  │ Step 1: Fetch accounts with pessimistic lock                │   │
│  │         └─ Prevents concurrent balance modifications        │   │
│  │                                                               │   │
│  │ Step 2: Validate balance (sender has sufficient funds)      │   │
│  │                                                               │   │
│  │ Step 3: Debit sender account (DEBIT entry)                 │   │
│  │         Credit receiver account (CREDIT entry)              │   │
│  │                                                               │   │
│  │ Step 4: Create LedgerEntry records (atomic transaction)    │   │
│  │         ├─ DEBIT: sender, -amount                           │   │
│  │         └─ CREDIT: receiver, +amount                        │   │
│  │                                                               │   │
│  │ Step 5: OutboxPatternWorker.saveOutboxEvent()              │   │
│  │         └─ Create OutboxEvent for async processing          │   │
│  │                                                               │   │
│  │ Guarantees: ACID transaction, ledger always balanced        │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ OutboxPatternWorker (Transactional Outbox)                   │   │
│  │                                                               │   │
│  │ Triggered by: LedgerService in same @Transactional scope    │   │
│  │                                                               │   │
│  │ Implementation:                                              │   │
│  │ ├─ Save OutboxEvent to PostgreSQL (same transaction)       │   │
│  │ ├─ Publish to Kafka in separate @Transactional method      │   │
│  │ │  (REQUIRES_NEW isolation: release DB lock before publish) │   │
│  │ └─ Kafka message: {transactionId, status, amount, provider} │   │
│  │                                                               │   │
│  │ Pattern Benefits:                                            │   │
│  │ ├─ Guaranteed delivery: Message persisted before dispatch    │   │
│  │ ├─ No dual-write problem: DB & messaging atomic            │   │
│  │ └─ Enables downstream services (notification, reporting)   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ MetricsService (Provider Health Monitoring)                  │   │
│  │                                                               │   │
│  │ Query: getHealth(providerId)                                 │   │
│  │                                                               │   │
│  │ Lookup order:                                                │   │
│  │ 1. Redis cache: "metrics:{providerId}:sr" (success rate)    │   │
│  │    + "metrics:{providerId}:p95" (95th percentile latency)   │   │
│  │ 2. Fallback: Default values if Redis miss                   │   │
│  │    (Resilience4j circuit breaker provides real-time health)  │   │
│  │                                                               │   │
│  │ Used by: SmartRoutingStrategy for provider scoring          │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  External Dependencies:                                              │
│  ├─ PostgreSQL (port 5432): Ledger, accounts, transactions        │
│  ├─ Redis (port 6379): Idempotency cache, metrics, locks         │
│  ├─ Kafka (port 9092): Outbox event streaming                    │
│  └─ Payment Gateways: Stripe, Razorpay (external APIs)            │
└─────────────────────────────────────────────────────────────────────┘
                          │
         ┌────────────────┼────────────────┐
         │                │                │
         ▼                ▼                ▼
    ┌─────────┐    ┌──────────┐    ┌─────────────┐
    │ Webhook │    │ Reconcil │    │ Notification
    │ Worker  │    │ Worker   │    │ Service
    │ (Port   │    │ (Batch   │    │ (Kafka)
    │ 8081)   │    │ Job)     │    │
    └─────────┘    └──────────┘    └─────────────┘
```

---

## Module Structure

### 1. **payorch-shared** (Shared Libraries)

**Purpose:** Common models, DTOs, and utilities used across all modules

**Key Components:**

#### Models (JPA Entities)

```
src/main/java/com/payorch/model/
├── Account.java
│   └─ Fields: id (UUID), owner_id, balance, currency, version, created_at
│   └─ Lock support: @Version for optimistic locking
│
├── Transaction.java
│   └─ Fields: id, idempotency_key, amount, currency, status, provider_id, provider_ref_id,
│              sender_account_id, receiver_account_id, created_at, updated_at
│   └─ Status enum: INITIATED, PENDING, SUCCESS, FAILED
│   └─ Relationships: @ManyToOne with Account (sender/receiver)
│
├── LedgerEntry.java
│   └─ Fields: id, transaction_id, account_id, amount, entry_type (DEBIT/CREDIT), created_at
│   └─ Purpose: Double-entry accounting record
│
├── IdempotencyKey.java
│   └─ Fields: id (string key), request_hash, response_payload, created_at, expires_at
│   └─ Purpose: Cache for idempotent replay protection
│
└── TransactionStatus.java
    └─ Enum for transaction states
```

#### DTOs

```
src/main/java/com/payorch/providers/dto/
├── ProviderResponse.java
│   └─ Fields: status (SUCCESS/FAILED), transactionId, errorMessage, externalRefId
│   └─ Serialized to idempotency cache
│
└── ProviderTransactionDetails.java
    └─ External provider status snapshot (used by reconciliation)
```

---

### 2. **core-orchestrator** (Main Payment Service)

**Port:** 8080  
**Responsibilities:** Payment orchestration, ledger management, state coordination

#### Controllers

```
src/main/java/com/payorch/orchestrator/controller/
└── CheckoutController.java
    ├─ POST /checkout
    │  ├─ Headers: X-Idempotency-Key (required)
    │  ├─ Body: {amount, currency, metadata}
    │  └─ Response: ProviderResponse
    │
    └─ Orchestrates: PaymentOrchestrator.processPayment()
```

#### Services

**1. PaymentOrchestrator** (`orchestrator/service/PaymentOrchestrator.java`)

- **Responsibility:** Main payment workflow orchestration
- **Method:** `processPayment(Transaction transaction)`

  ```
  1. Check idempotency cache (Redis + DB)
     └─ Return cached response if exists
  2. Acquire distributed lock
  3. Select best provider (SmartRoutingStrategy)
  4. Initialize payment state in DB
  5. Execute payment with Resilience4j circuit breaker
  6. Finalize payment state (update status)
  7. Save response to idempotency cache (write-through)
  8. Release lock

  Error Handling:
  ├─ Provider fails → Recursive failover to next provider
  ├─ All providers exhausted → Fatal error
  └─ Lock acquisition fails → Concurrent request detected
  ```

**2. SmartRoutingStrategy** (`orchestrator/service/SmartRoutingStrategy.java`)

- **Responsibility:** Dynamic provider selection based on health metrics
- **Algorithm:**

  ```
  Score each provider:
  ├─ Success rate (60% weight): From MetricsService
  ├─ Latency (20% weight): P95 latency
  ├─ Cost (10% weight): Provider fee
  ├─ Circuit breaker state (10% weight): Resilience4j status
  └─ Exclude failed providers from current request

  Return: Provider with highest score
  ```

**3. PaymentStateManager** (`orchestrator/service/PaymentStateManager.java`)

- **Responsibility:** Transaction state lifecycle management
- **Methods:**

  ```
  initializePaymentState(Transaction, providerId)
  ├─ Set status: INITIATED
  ├─ Store provider_id
  └─ Persist to DB

  finalizePaymentState(Transaction, ProviderResponse)
  ├─ Update status: SUCCESS or FAILED
  ├─ Store provider_ref_id (external transaction ID)
  └─ Trigger LedgerService if successful

  handleLocalFailureState(Transaction, reason)
  └─ Update status: FAILED with reason
  ```

**4. LedgerService** (`ledger/service/LedgerService.java`)

- **Responsibility:** Double-entry accounting
- **Method:** `recordEntry(Transaction transaction)`

  ```
  Transaction scope: @Transactional(rollbackFor = Exception.class)

  1. Fetch sender & receiver accounts with pessimistic lock
     └─ @Query("SELECT a FROM Account a WHERE a.id = :id")
        + @Lock(LockModeType.PESSIMISTIC_WRITE)

  2. Validate sender balance >= transaction amount

  3. Modify account balances:
     └─ sender.balance -= amount
     └─ receiver.balance += amount

  4. Create double-entry records:
     └─ LedgerEntry(txn, sender, -amount, DEBIT)
     └─ LedgerEntry(txn, receiver, +amount, CREDIT)

  5. OutboxPatternWorker.saveOutboxEvent(transaction)
     └─ Enqueues async notification

  Guarantees:
  ├─ Atomic transaction (all-or-nothing)
  ├─ Ledger always balanced (DEBIT = CREDIT)
  └─ No concurrent account modifications
  ```

**5. MetricsService** (`orchestrator/service/MetricsService.java`)

- **Responsibility:** Provider health monitoring
- **Method:** `getHealth(String pspId)`

  ```
  Return PSPHealth object:
  ├─ providerId
  ├─ successRate: % of successful transactions
  ├─ p95Latency: 95th percentile response time (ms)
  ├─ costBase: Transaction fee
  └─ isActive: Service availability

  Lookup order:
  1. Redis cache (updated by metrics pipeline)
  2. Default fallback (0.90 success, 300ms latency)
  ```

**6. OutboxPatternWorker** (`outbox/service/OutboxPatternWorker.java`)

- **Responsibility:** Transactional Outbox pattern implementation
- **Method:** `saveOutboxEvent(OutboxEvent event)`

  ```
  @Transactional(rollbackFor = Exception.class)
  Step 1: Persist OutboxEvent to DB (in ledger transaction)

  @Transactional(propagation = REQUIRES_NEW)
  Step 2: Publish to Kafka (separate transaction after ledger commit)
       └─ Topic: "payment-events"
       └─ Payload: {transactionId, status, amount, provider}

  Benefits:
  ├─ Guaranteed at-least-once delivery
  ├─ No dual-write problem
  └─ Ledger-messaging consistency
  ```

**7. IdempotencyManager** (`common/idempotency/IdempotencyManager.java`)

- **Responsibility:** Idempotency cache + distributed lock management
- **Methods:**

  ```
  getResponse(String key)
  ├─ L1 Cache: Check Redis "resp:payment:{key}"
  ├─ L2 Cache: Fall back to DB if service restarted
  └─ Return cached ProviderResponse JSON or null

  acquireLock(String key)
  ├─ SET NX (Redis): "lock:payment:{key}" = "PROCESSING"
  ├─ TTL: 5 minutes (auto-expire on crash)
  └─ Return: true if acquired, false if held

  saveResponse(String key, String responseJson)
  ├─ Write to PostgreSQL idempotency_keys table
  ├─ Update Redis cache (24h TTL)
  └─ Release distributed lock

  cleanupExpiredEntries()
  └─ Scheduled daily (2 AM UTC) via ShedLock

  validateRequestHash(String key, String hash)
  └─ Detect divergent replays (modified request in retry)
  ```

**8. IdempotencyCleanupScheduler** (`common/idempotency/IdempotencyCleanupScheduler.java`)

- **Responsibility:** Scheduled cleanup of expired idempotency entries
- **Trigger:** Cron: 0 0 2 \* \* \* (2 AM UTC daily)
- **Lock:** ShedLock ensures single execution across cluster

#### Repositories

```
src/main/java/com/payorch/ledger/repository/
├── TransactionRepository extends JpaRepository<Transaction, UUID>
│   └─ findByProviderRefId(String refId)
│
├── AccountRepository extends JpaRepository<Account, UUID>
│   └─ findByIdWithLock(UUID id) with @Lock(PESSIMISTIC_WRITE)
│
├── LedgerRepository extends JpaRepository<LedgerEntry, UUID>
│
└── IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String>
    ├─ deleteExpiredEntries(LocalDateTime now)
    └─ deleteOlderThanTtl(LocalDateTime cutoff)
```

#### Configuration

```
src/main/resources/
├── application.yml
│   ├─ spring.datasource.url: PostgreSQL connection
│   ├─ spring.redis.host: Redis connection
│   ├─ spring.kafka.bootstrap-servers: Kafka brokers
│   ├─ payorch.idempotency.ttl-hours: 24
│   └─ resilience4j.circuitbreaker: Provider circuit breaker config
│
└── db/migration/
    ├─ V1__init_payorch_schema.sql (Initial schema)
    ├─ V2__add_reconciliation_and_shedlock.sql (Reconciliation tables)
    ├─ V3__add_missing_entity_columns.sql (Account relationships)
    ├─ V4__drop_provider_health_and_provider_configs.sql (Cleanup)
    └─ V5__enhance_idempotency_with_database_persistence.sql (Idempotency schema)
```

---

### 3. **webhook-worker** (Provider Webhook Handler)

**Port:** 8081  
**Responsibility:** Receive payment status updates from external providers

**Key Flow:**

```
POST /webhook/payment-status
├─ Body: {transactionId, externalRefId, status, timestamp}
├─ Webhook signature validation (security)
├─ Find Transaction by externalRefId
├─ Update transaction status
└─ Publish to Kafka for downstream services

Example providers:
├─ Stripe: POST /webhook/stripe/event
├─ Razorpay: POST /webhook/razorpay/charge.authorized
└─ Custom: POST /webhook/custom/status
```

---

### 4. **reconciliation-worker** (Batch Reconciliation Service)

**Trigger:** Scheduled job (e.g., every 6 hours)  
**Responsibility:** Compare internal state vs. external provider state

**Architecture:**

```
ReconciliationScheduler (Entry point)
├─ Cron: "0 0 */6 * * *" (every 6 hours)
├─ ShedLock: Ensures single execution across cluster
│
└─ ReconciliationBatchConfig (Spring Batch)
    ├─ TransactionItemReader
    │  └─ JpaPagingItemReader<Transaction>
    │     └─ Query: "SELECT t FROM Transaction t WHERE t.updatedAt >= :lookbackTime AND t.status = 'PENDING'"
    │        (Reads unreconciled transactions in batches)
    │
    ├─ TransactionItemProcessor
    │  ├─ For each transaction:
    │  │  ├─ Fetch external status via provider.fetchStatus(providerRefId)
    │  │  ├─ Compare: internal status vs. external status
    │  │  └─ If mismatch: Create ReconciliationMismatch record
    │  │
    │  └─ Critical discrepancies detected:
    │     ├─ PENDING locally, SUCCESS externally → Payment succeeded without local update
    │     ├─ SUCCESS locally, FAILED externally → Payment failed despite local success
    │     └─ FAILED locally, PENDING externally → Provider still processing
    │
    ├─ TransactionItemWriter
    │  └─ Batch write ReconciliationMismatch records to DB
    │     (Log all discrepancies for manual review)
    │
    └─ Job execution:
       ├─ Chunk size: 100 transactions
       ├─ Error handling: Retry policy with exponential backoff
       └─ Completion: Store reconciliation_mismatches for investigation

Output:
└─ reconciliation_mismatches table populated with:
   ├─ transactionId
   ├─ internalStatus
   ├─ externalStatus
   ├─ resolutionStatus (PENDING_INVESTIGATION, AUTO_RESOLVED, MANUAL_RESOLVED)
   └─ Timestamp for audit trail
```

---

## Technology Stack

| Component            | Technology        | Purpose                         |
| -------------------- | ----------------- | ------------------------------- |
| **Framework**        | Spring Boot 3.3.x | Microservices foundation        |
| **Language**         | Java 21           | Enterprise-grade JVM            |
| **ORM**              | Hibernate JPA     | Object-relational mapping       |
| **Database**         | PostgreSQL 15+    | Relational ledger storage       |
| **Cache**            | Redis 7.x         | L1 cache, distributed locks     |
| **Messaging**        | Apache Kafka 3.x  | Event streaming, outbox pattern |
| **Resilience**       | Resilience4j 2.x  | Circuit breakers, retry logic   |
| **Distributed Lock** | ShedLock 5.x      | Cluster-safe scheduling         |
| **Batch Processing** | Spring Batch 5.x  | Reconciliation jobs             |
| **Build**            | Maven 3.9.x       | Dependency management           |
| **Logging**          | SLF4J + Logback   | Structured logging              |
| **Testing**          | JUnit 5 + Mockito | Unit & integration tests        |

---

## Database Schema

### Core Tables

#### accounts

```sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id VARCHAR(255) NOT NULL,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,  -- For optimistic locking
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### transactions

```sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,  -- INITIATED, PENDING, SUCCESS, FAILED
    provider_id VARCHAR(50),  -- Selected payment provider
    provider_ref_id VARCHAR(255),  -- External provider's transaction ID
    failure_reason TEXT,
    sender_account_id UUID,  -- FK to accounts
    receiver_account_id UUID,  -- FK to accounts
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### ledger_entries

```sql
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,  -- FK to transactions
    account_id UUID NOT NULL,  -- FK to accounts
    amount DECIMAL(19, 4) NOT NULL,
    entry_type VARCHAR(10) NOT NULL,  -- DEBIT or CREDIT
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### idempotency_keys

```sql
CREATE TABLE idempotency_keys (
    id VARCHAR(255) PRIMARY KEY,  -- Idempotency key from client
    request_hash VARCHAR(64),  -- SHA-256 of request body
    response_payload TEXT,  -- Serialized ProviderResponse
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP  -- TTL for cleanup
);
```

#### outbox

```sql
CREATE TABLE outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,  -- 'payment.completed', 'payment.failed', etc.
    payload JSONB,  -- Event data
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSED, FAILED
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### reconciliation_mismatches

```sql
CREATE TABLE reconciliation_mismatches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,  -- FK to transactions
    provider_ref_id VARCHAR(255),
    internal_status VARCHAR(20) NOT NULL,  -- Our recorded status
    external_status VARCHAR(20) NOT NULL,  -- Provider's status
    resolution_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_INVESTIGATION',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### shedlock

```sql
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
```

---

## Request Flow: Checkout to Settlement

### Scenario: User processes a $100 payment from Account A to Account B via Stripe

```
Timeline: T=0ms → T=5000ms

T=0ms
┌─────────────────────────────────────────────────────────────┐
│ Client sends POST /checkout                                 │
│ Headers: X-Idempotency-Key: "abcd-1234"                    │
│ Body: {amount: 100, currency: USD}                         │
└─────────────────────────────────────────────────────────────┘
                           ↓
T=1ms
┌─────────────────────────────────────────────────────────────┐
│ CheckoutController.checkout()                               │
│ ├─ Validate idempotency key exists                         │
│ ├─ Validate amount > 0                                      │
│ ├─ Create Transaction entity                                │
│ │  ├─ id: UUID-generated                                   │
│ │  ├─ idempotencyKey: "abcd-1234"                          │
│ │  ├─ amount: 100.00                                        │
│ │  ├─ currency: USD                                         │
│ │  ├─ status: INITIATED                                     │
│ │  ├─ senderAccount: Account-A                              │
│ │  ├─ receiverAccount: Account-B                            │
│ │  └─ createdAt: NOW()                                      │
│ │                                                             │
│ └─ Call PaymentOrchestrator.processPayment(transaction)    │
└─────────────────────────────────────────────────────────────┘
                           ↓
T=2ms
┌─────────────────────────────────────────────────────────────┐
│ PaymentOrchestrator.processPayment()                        │
│                                                              │
│ IDEMPOTENCY CHECK:                                           │
│ ├─ IdempotencyManager.getResponse("abcd-1234")             │
│ │  ├─ Check Redis: "resp:payment:abcd-1234"                │
│ │  │  └─ MISS (first request)                              │
│ │  └─ Check DB: SELECT * FROM idempotency_keys WHERE id... │
│ │     └─ MISS (no prior execution)                         │
│ │                                                            │
│ └─ Continue to step: ACQUIRE LOCK                          │
│                                                              │
│ DISTRIBUTED LOCK:                                            │
│ ├─ IdempotencyManager.acquireLock("abcd-1234")             │
│ │  ├─ Redis SET NX: "lock:payment:abcd-1234" = "PROCESSING"
│ │  ├─ TTL: 5 minutes (auto-expire on crash)                │
│ │  └─ SUCCESS: Lock acquired                                │
│ │                                                            │
│ └─ Continue to step: SELECT PROVIDER                       │
└─────────────────────────────────────────────────────────────┘
                           ↓
T=5ms
┌─────────────────────────────────────────────────────────────┐
│ SmartRoutingStrategy.selectBestProvider()                   │
│                                                              │
│ Evaluate all available providers:                            │
│                                                              │
│ Provider: STRIPE                                             │
│ ├─ MetricsService.getHealth("STRIPE")                      │
│ │  ├─ Redis: "metrics:STRIPE:sr" = 0.985                  │
│ │  ├─ Redis: "metrics:STRIPE:p95" = 145                   │
│ │  └─ Return: {successRate: 0.985, p95Latency: 145}       │
│ ├─ Score calculation:                                       │
│ │  ├─ Success rate: 0.985 * 0.60 = 0.591                  │
│ │  ├─ Latency: (1 - 145/1000) * 0.20 = 0.171              │
│ │  ├─ Cost: (1 - 0.02/1.00) * 0.10 = 0.098                │
│ │  ├─ CB state: CLOSED (normal) = 1.0 * 0.10 = 0.10       │
│ │  └─ Total score: 0.591 + 0.171 + 0.098 + 0.10 = 0.960  │
│                                                              │
│ Provider: RAZORPAY                                           │
│ ├─ Score: 0.825 (lower success rate)                       │
│                                                              │
│ Provider: PAYPAL                                             │
│ ├─ Score: 0.450 (circuit breaker OPEN, down for maintenance)
│                                                              │
│ ► SELECTED PROVIDER: STRIPE (highest score: 0.960)         │
└─────────────────────────────────────────────────────────────┘
                           ↓
T=10ms
┌─────────────────────────────────────────────────────────────┐
│ executeWithFailover() [Recursive call #1]                   │
│                                                              │
│ INITIALIZE PAYMENT STATE:                                    │
│ ├─ PaymentStateManager.initializePaymentState(txn, "STRIPE")
│ │  ├─ Update transaction.status = PENDING                  │
│ │  ├─ Update transaction.providerId = "STRIPE"             │
│ │  ├─ Transaction.save()                                    │
│ │  └─ DB: INSERT transaction... OR UPDATE transaction...   │
│ │                                                            │
│ └─ Continue to step: EXECUTE WITH CIRCUIT BREAKER          │
│                                                              │
│ RESILIENCE4J CIRCUIT BREAKER:                                │
│ ├─ CircuitBreaker circuitBreaker = registry.circuitBreaker()
│ │  (name: "paymentProviderCircuit-STRIPE")                 │
│ │                                                            │
│ ├─ circuitBreaker.executeSupplier(                         │
│ │    () -> stripeProvider.process(transaction)             │
│ │  )                                                         │
│ │                                                            │
│ └─ Inside circuit breaker:                                  │
└─────────────────────────────────────────────────────────────┘
                           ↓
T=50ms → T=200ms (Network call to Stripe)
┌─────────────────────────────────────────────────────────────┐
│ StripePaymentProvider.process(transaction)                  │
│                                                              │
│ ├─ Prepare Stripe API request:                             │
│ │  ├─ amount: 10000 (cents)                                │
│ │  ├─ currency: usd                                         │
│ │  ├─ idempotencyKey: "abcd-1234" (Stripe also supports)  │
│ │  ├─ metadata: {senderAccountId, receiverAccountId}       │
│ │  └─ description: "Payment from Account-A to Account-B"   │
│ │                                                            │
│ ├─ STRIPE API CALL (external):                              │
│ │  └─ POST https://api.stripe.com/v1/charges               │
│ │     ↓                                                      │
│ │     Stripe processes payment (100ms)                      │
│ │     ↓                                                      │
│ │     Returns: {                                            │
│ │       id: "ch_1ABC123...",                               │
│ │       status: "succeeded",                                │
│ │       amount: 10000,                                      │
│ │       currency: "usd"                                     │
│ │     }                                                      │
│ │                                                            │
│ └─ Return ProviderResponse:                                 │
│    ├─ status: SUCCESS                                       │
│    ├─ externalRefId: "ch_1ABC123..."                       │
│    └─ transactionId: (our UUID)                            │
└─────────────────────────────────────────────────────────────┘
                           ↓
T=210ms
┌─────────────────────────────────────────────────────────────┐
│ executeWithFailover() [Return from Stripe call]             │
│                                                              │
│ FINALIZE PAYMENT STATE:                                      │
│ ├─ PaymentStateManager.finalizePaymentState(txn, response) │
│ │  ├─ transaction.status = SUCCESS                         │
│ │  ├─ transaction.providerRefId = "ch_1ABC123..."          │
│ │  ├─ transaction.updatedAt = NOW()                        │
│ │  ├─ transaction.save()                                    │
│ │  └─ DB: UPDATE transactions SET status='SUCCESS', ...    │
│ │                                                            │
│ └─ Return ProviderResponse to caller                        │
└─────────────────────────────────────────────────────────────┘
                           ↓
T=212ms
┌─────────────────────────────────────────────────────────────┐
│ PaymentOrchestrator.processPayment() [Back to main]         │
│                                                              │
│ SAVE RESPONSE (Write-through cache):                         │
│ ├─ IdempotencyManager.saveResponse("abcd-1234", json)      │
│ │  ├─ @Transactional method:                               │
│ │  │  ├─ IdempotencyKey entry = new IdempotencyKey(...)   │
│ │  │  │  ├─ id: "abcd-1234"                               │
│ │  │  │  ├─ responsePayload: "{status: SUCCESS, ...}"    │
│ │  │  │  ├─ expiresAt: NOW() + 24 hours                   │
│ │  │  │  └─ Save to DB                                     │
│ │  │  │                                                      │
│ │  │  ├─ Redis SET: "resp:payment:abcd-1234" = json       │
│ │  │  │  └─ TTL: 24 hours                                  │
│ │  │  │                                                      │
│ │  │  └─ IdempotencyManager.releaseLock("abcd-1234")      │
│ │  │     └─ Redis DELETE: "lock:payment:abcd-1234"         │
│ │  │                                                        │
│ │  └─ Return to PaymentOrchestrator                        │
│ │                                                            │
│ └─ Exception handling:                                       │
│    └─ If error: Call releaseLock() in finally block        │
│                                                              │
│ Return ProviderResponse to CheckoutController               │
└─────────────────────────────────────────────────────────────┘
                           ↓
T=215ms
┌─────────────────────────────────────────────────────────────┐
│ CheckoutController returns response to client               │
│                                                              │
│ HTTP 200 OK:                                                 │
│ {                                                             │
│   "status": "SUCCESS",                                       │
│   "transactionId": "550e8400-e29b-41d4-a716-446655440000", │
│   "externalRefId": "ch_1ABC123...",                         │
│   "amount": 100.00,                                          │
│   "currency": "USD"                                          │
│ }                                                             │
└─────────────────────────────────────────────────────────────┘

T=220ms (ASYNC: Ledger processing triggered)
┌─────────────────────────────────────────────────────────────┐
│ LedgerService.recordEntry(transaction)                      │
│                                                              │
│ @Transactional(rollbackFor = Exception.class):               │
│                                                              │
│ 1. FETCH ACCOUNTS WITH LOCK:                                │
│    ├─ accountRepository.findByIdWithLock(senderAccountId)  │
│    │  ├─ SELECT a FROM Account a WHERE a.id = ?            │
│    │  ├─ @Lock(LockModeType.PESSIMISTIC_WRITE)             │
│    │  └─ FOR UPDATE (row lock in PostgreSQL)               │
│    │     Account-A {balance: 5000.00, ...}                 │
│    │                                                         │
│    └─ accountRepository.findByIdWithLock(receiverAccountId) │
│       └─ Account-B {balance: 3000.00, ...}                 │
│                                                              │
│ 2. VALIDATE:                                                 │
│    ├─ sender.balance (5000) >= amount (100) ? YES           │
│    └─ Continue to step: MODIFY BALANCES                     │
│                                                              │
│ 3. MODIFY BALANCES:                                          │
│    ├─ sender.balance = 5000.00 - 100.00 = 4900.00          │
│    ├─ receiver.balance = 3000.00 + 100.00 = 3100.00        │
│    │                                                         │
│    └─ accountRepository.save(sender)                        │
│       accountRepository.save(receiver)                       │
│          → UPDATE accounts SET balance = 4900.00 WHERE ...  │
│                                                              │
│ 4. CREATE LEDGER ENTRIES (DEBIT/CREDIT):                    │
│    ├─ LedgerEntry debitEntry = new LedgerEntry(...)        │
│    │  ├─ transaction: transaction                           │
│    │  ├─ account: Account-A                                 │
│    │  ├─ amount: -100.00 (DEBIT)                           │
│    │  ├─ entryType: "DEBIT"                                 │
│    │  └─ ledgerRepository.save(debitEntry)                 │
│    │     → INSERT INTO ledger_entries (txn, acc, amt, type)
│    │                                                         │
│    └─ LedgerEntry creditEntry = new LedgerEntry(...)       │
│       ├─ transaction: transaction                           │
│       ├─ account: Account-B                                 │
│       ├─ amount: 100.00 (CREDIT)                           │
│       ├─ entryType: "CREDIT"                                │
│       └─ ledgerRepository.save(creditEntry)                 │
│                                                              │
│ 5. SAVE OUTBOX EVENT:                                        │
│    └─ OutboxPatternWorker.saveOutboxEvent(transaction)      │
│       ├─ OutboxEvent event = new OutboxEvent(...)          │
│       │  ├─ eventType: "payment.completed"                  │
│       │  ├─ payload: {txnId, status, amount, provider}     │
│       │  ├─ status: "PENDING"                               │
│       │  └─ outboxRepository.save(event)                   │
│       │     → INSERT INTO outbox (event_type, payload)     │
│       │                                                      │
│       └─ Transaction COMMIT (Ledger + Outbox in same TX)   │
│                                                              │
│ GUARANTEE: Ledger balanced                                   │
│ ├─ DEBIT (Account-A): -100.00                              │
│ ├─ CREDIT (Account-B): +100.00                             │
│ └─ Sum: 0.00 ✓                                              │
└─────────────────────────────────────────────────────────────┘

T=230ms (ASYNC: Kafka publish)
┌─────────────────────────────────────────────────────────────┐
│ OutboxPatternWorker.publishOutboxEvent()                    │
│ (Separate @Transactional(REQUIRES_NEW) method)              │
│                                                              │
│ ├─ Publish to Kafka topic: "payment-events"                │
│ │  ├─ Key: transactionId                                    │
│ │  └─ Value: {                                              │
│ │      "transactionId": "550e8400...",                     │
│ │      "status": "SUCCESS",                                 │
│ │      "amount": 100.00,                                    │
│ │      "currency": "USD",                                   │
│ │      "provider": "STRIPE",                                │
│ │      "timestamp": "2026-06-18T22:35:00Z"                 │
│ │    }                                                       │
│ │                                                            │
│ └─ Update OutboxEvent.status = "PROCESSED"                │
│    └─ DB: UPDATE outbox SET status='PROCESSED' WHERE id=...
│                                                              │
│ Downstream consumers listen to "payment-events":            │
│ ├─ Notification Service: Send email/SMS to users           │
│ ├─ Analytics Service: Record transaction metrics            │
│ ├─ Reporting Service: Update dashboard                      │
│ └─ Webhook Service: Notify merchant                        │
└─────────────────────────────────────────────────────────────┘

T=5000ms (6 hours later: Reconciliation batch job)
┌─────────────────────────────────────────────────────────────┐
│ ReconciliationScheduler.cleanupExpiredIdempotencyKeys()    │
│ (Daily cleanup: 2 AM UTC)                                    │
│                                                              │
│ Scheduled with ShedLock:                                     │
│ ├─ Only ONE node in cluster executes this                   │
│ ├─ lockAtMostFor: 10 minutes                                │
│ └─ lockAtLeastFor: 5 minutes                                │
│                                                              │
│ Execution:                                                   │
│ ├─ IdempotencyManager.cleanupExpiredEntries()              │
│ │  ├─ DELETE FROM idempotency_keys                         │
│ │  │  WHERE expires_at IS NOT NULL AND expires_at < NOW()  │
│ │  ├─ DELETE FROM idempotency_keys                         │
│ │  │  WHERE expires_at IS NULL AND created_at < (NOW()-24h)│
│ │  └─ Log: "Cleaned up 1234 expired entries"               │
│ │                                                            │
│ └─ Release ShedLock lock                                    │
└─────────────────────────────────────────────────────────────┘

T=6hours (Next reconciliation window)
┌─────────────────────────────────────────────────────────────┐
│ ReconciliationScheduler.runReconciliation()                 │
│ (Scheduled: every 6 hours)                                   │
│                                                              │
│ ├─ TransactionItemReader reads all PENDING transactions    │
│ │  └─ JPA Batch reader: 100 items per batch                │
│ │                                                            │
│ ├─ TransactionItemProcessor compares internal vs external   │
│ │  ├─ stripeProvider.fetchStatus("ch_1ABC123...")          │
│ │  └─ Compare: Local (SUCCESS) vs Stripe (succeeded) → OK  │
│ │                                                            │
│ ├─ TransactionItemWriter saves reconciliation results       │
│ │  ├─ If mismatch found:                                    │
│ │  │  └─ INSERT reconciliation_mismatches (txn, internal,   │
│ │  │     external, PENDING_INVESTIGATION)                  │
│ │  │                                                         │
│ │  └─ If match: No record (implicit success)               │
│ │                                                            │
│ └─ Completed: Batch job finishes                            │
└─────────────────────────────────────────────────────────────┘
```

---

## Idempotency Mechanism

### Problem

Payment processing is inherently idempotent from a business perspective:

- Retrying the same $100 payment should result in only ONE charge
- Network failures, timeouts, and retries are common

### Solution: Write-Through Caching with Distributed Locking

#### Cache Hierarchy (L1 → L2)

| Layer  | Storage    | Latency | Purpose               | TTL      |
| ------ | ---------- | ------- | --------------------- | -------- |
| **L1** | Redis      | <1ms    | Fast replay detection | 24 hours |
| **L2** | PostgreSQL | ~10ms   | Durability + recovery | 24 hours |

#### Idempotency Key

Client provides unique identifier (typically UUID):

```
Header: X-Idempotency-Key: "550e8400-e29b-41d4-a716-446655440000"
```

#### Processing Logic

**First Request (Idempotency Key = "abc123")**

```
1. IdempotencyManager.getResponse("abc123")
   ├─ Redis.get("resp:payment:abc123") → null
   ├─ DB.select("SELECT * FROM idempotency_keys WHERE id='abc123'") → empty
   └─ Return: null (not cached)

2. IdempotencyManager.acquireLock("abc123")
   ├─ Redis.setNX("lock:payment:abc123", "PROCESSING", 5m)
   ├─ success = true
   └─ Proceed to execute

3. [Execute payment with Stripe API]
   └─ ProviderResponse: {status: SUCCESS, ...}

4. IdempotencyManager.saveResponse("abc123", responseJson)
   ├─ Write to DB:
   │  ├─ INSERT idempotency_keys (
   │  │    id='abc123',
   │  │    request_hash='sha256(...)',
   │  │    response_payload='{...}',
   │  │    created_at=NOW(),
   │  │    expires_at=NOW()+24h
   │  │  )
   │  └─ Transaction: COMMIT
   │
   ├─ Update Redis:
   │  └─ Redis.set("resp:payment:abc123", responseJson, 24h)
   │
   └─ Release lock:
      └─ Redis.delete("lock:payment:abc123")

5. Return ProviderResponse to client
```

**Retry Request (Same Idempotency Key = "abc123")**

```
1. IdempotencyManager.getResponse("abc123")
   ├─ Redis.get("resp:payment:abc123") → responseJson (HIT)
   └─ Return cached response immediately

2. [Skip payment execution]
   └─ No network call to Stripe (idempotent)

3. Return same response to client
   └─ Client cannot distinguish between first and retry
```

**After Service Restart (Data in DB, not Redis)**

```
1. IdempotencyManager.getResponse("abc123")
   ├─ Redis.get("resp:payment:abc123") → null (MISS)
   ├─ DB.select("SELECT * FROM idempotency_keys WHERE id='abc123'") → FOUND
   ├─ Warm Redis: set("resp:payment:abc123", cached_payload, 24h)
   └─ Return cached response

2. [Skip payment execution]
   └─ Database fallback ensures idempotency survives restarts
```

#### Request Hash Validation (Divergent Replay Detection)

Security measure: Detect if client retries with a **different request body**.

```
Scenario: Client's first request failed (e.g., network timeout)
├─ Time T: Client sends {"amount": 100, ...}
│          Server: Processes, stores response, crashes
│          Client: Timeout (doesn't know if payment succeeded)
│
└─ Time T+5s: Client retries with DIFFERENT amount {"amount": 200, ...}
   ├─ IdempotencyManager.validateRequestHash()
   │  ├─ Old request hash: SHA256("{amount: 100, ...}") = ABC123
   │  ├─ New request hash: SHA256("{amount: 200, ...}") = XYZ789
   │  ├─ Match? NO → DIVERGENT REPLAY DETECTED
   │  └─ Return: false + log security alert
   │
   └─ Server rejects: HTTP 409 Conflict
      └─ Message: "Request body mismatch for idempotency key"
```

Implementation:

```java
// In PaymentOrchestrator.processPayment()
String requestHash = DigestUtils.sha256Hex(objectMapper.writeValueAsString(request));
if (!idempotencyManager.validateRequestHash(key, requestHash)) {
    throw new DivergentReplayException("Request modified in retry");
}
```

---

## Resilience & Circuit Breakers

### Problem

Payment gateways can fail temporarily (maintenance, network issues, rate limits). Naive retry loops:

- Hammer failing provider → DDoS-like behavior
- Block other requests → Resource exhaustion
- No smart provider switching → Poor UX

### Solution: Resilience4j Circuit Breaker + Failover

#### Circuit Breaker States

```
CLOSED (Normal)
├─ State: All requests pass through
├─ Transitions:
│  └─ failure_rate > threshold (e.g., 50%) → OPEN
│
OPEN (Circuit Broken)
├─ State: All requests rejected immediately
├─ Fast-fail without calling provider (prevents cascading failure)
├─ Transitions:
│  └─ Wait timeout (e.g., 30s) → HALF_OPEN
│
HALF_OPEN (Testing Recovery)
├─ State: Allow limited requests to test if provider recovered
├─ Transitions:
│  ├─ Success rate > threshold → CLOSED (recovered)
│  └─ Failure detected → OPEN (still broken)
```

#### Configuration (application.yml)

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50 # Open if 50%+ fail
        wait-duration-in-open-state: 30000 # Wait 30s before trying again
        permitted-number-of-calls-in-half-open-state: 5 # Test with 5 calls
        minimum-number-of-calls: 10 # Ignore if < 10 calls

    instances:
      paymentProviderCircuit-STRIPE:
        base-config: default
      paymentProviderCircuit-RAZORPAY:
        base-config: default
```

#### Execution Flow with Failover

```
SmartRoutingStrategy.selectBestProvider()
│
├─ Provider 1: STRIPE
│  └─ Circuit breaker: OPEN (failing)
│  └─ Exclude from selection
│
├─ Provider 2: RAZORPAY
│  └─ Circuit breaker: CLOSED (healthy)
│  └─ Score: highest
│  └─ Selected
│
└─ Provider 3: PAYPAL
   └─ Circuit breaker: HALF_OPEN (testing)
   └─ Low priority

executeWithFailover(Transaction, attemptedProviderIds=[])
│
├─ Iteration 1:
│  ├─ providerId = "RAZORPAY"
│  ├─ attemptedProviderIds.add("RAZORPAY")
│  ├─ Try: circuitBreaker("RAZORPAY").executeSupplier(...)
│  │  ├─ Success → ProviderResponse (SUCCESS)
│  │  └─ Return response
│  │
│  └─ Catch:
│     ├─ Failure detected
│     ├─ Check: available_count (3) > attempted.size (1) ?
│     │         3 > 1 → YES, retry with next provider
│     │
│     └─ Recursive call: executeWithFailover(txn, ["RAZORPAY"])
│
├─ Iteration 2:
│  ├─ providerId = "PAYPAL" (next best, skipping RAZORPAY)
│  ├─ attemptedProviderIds.add("PAYPAL")
│  ├─ Try: circuitBreaker("PAYPAL").executeSupplier(...)
│  │  └─ Success → ProviderResponse
│  │
│  └─ Catch:
│     ├─ Failure
│     ├─ Check: 3 > 2 ? YES, retry again
│     └─ Recursive: executeWithFailover(txn, ["RAZORPAY", "PAYPAL"])
│
└─ Iteration 3:
   ├─ providerId = "STRIPE" (last remaining)
   ├─ attemptedProviderIds.add("STRIPE")
   ├─ Try: circuitBreaker("STRIPE").executeSupplier(...)
   │  └─ Fails
   │
   └─ Catch:
      ├─ Check: 3 > 3 ? NO
      └─ Throw: "All providers exhausted"
         → HTTP 500 error returned to client
         → Transaction marked as FAILED
```

---

## Event-Driven Architecture (Outbox Pattern)

### Problem

In distributed systems, maintaining consistency between database and external systems is hard:

```
Naive approach:
1. Update database
2. Publish to Kafka
   └─ Problem: Service crashes between steps → Message lost or payment not recorded

Better approach:
1. Update database + create outbox record (SAME transaction)
2. Publish to Kafka (separate transaction after commit)
   └─ If Kafka fails: Retry later (message persisted in DB)
```

### Transactional Outbox Pattern

#### Architecture

```
LedgerService.recordEntry()
└─ @Transactional(rollbackFor = Exception.class)
   ├─ Modify account balances
   ├─ Create ledger entries
   ├─ OutboxPatternWorker.saveOutboxEvent()
   │  └─ @Transactional (same scope)
   │     └─ INSERT INTO outbox (event_type, payload, status='PENDING')
   │
   └─ Transaction COMMIT
      └─ All changes atomic: accounts + ledger + outbox

OutboxPatternWorker.publishOutboxEvent()
└─ @Transactional(propagation = REQUIRES_NEW)
   ├─ Fetch pending outbox records
   ├─ Publish to Kafka: "payment-events" topic
   │  ├─ Key: transactionId
   │  ├─ Value: {txnId, status, amount, provider, timestamp}
   │  └─ Partition determined by key (deterministic ordering)
   │
   └─ UPDATE outbox SET status='PROCESSED' WHERE id=...
      └─ Commit (separate transaction)
```

#### Guaranteed Delivery

```
Scenario 1: Normal flow
┌─────────────────────────┐
│ 1. Record ledger entry  │
│    + create outbox      │
│    (COMMIT)             │
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│ 2. Publish to Kafka     │
│    (COMMIT)             │
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│ 3. Mark outbox          │
│    PROCESSED            │
│    (COMMIT)             │
└────────────┬────────────┘
             ▼
        [SUCCESS]

Scenario 2: Service crashes during Kafka publish
┌─────────────────────────┐
│ 1. Record ledger entry  │
│    + create outbox      │
│    (COMMIT)             │
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│ 2. Publish to Kafka     │
│    [CRASH MID-PUBLISH]  │
└────────────┬────────────┘
             ▼
      [SERVICE RESTART]
             ▼
┌─────────────────────────┐
│ 3. Background job finds │
│    outbox.status='      │
│    PENDING'             │
│    Retries publishing   │
│    (Kafka idempotent)   │
└────────────┬────────────┘
             ▼
        [SUCCESS]

Key guarantee: Message is ALWAYS published (eventually)
because it's persisted in the database.
```

#### Kafka Message Format

```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SUCCESS",
  "amount": 100.0,
  "currency": "USD",
  "provider": "STRIPE",
  "externalRefId": "ch_1ABC123...",
  "senderAccountId": "acc-001",
  "receiverAccountId": "acc-002",
  "timestamp": "2026-06-18T22:35:00Z"
}
```

#### Downstream Consumers

Kafka topic: `payment-events`

| Consumer             | Purpose                | Action                                       |
| -------------------- | ---------------------- | -------------------------------------------- |
| Notification Service | User notifications     | Send email/SMS: "Payment of $100 successful" |
| Analytics Service    | Metrics/dashboards     | Record: txn count, volume, success rate      |
| Reporting Service    | Business intelligence  | Update charts, daily reports                 |
| Webhook Service      | Merchant notifications | Call merchant's webhook URL                  |
| Fraud Detection      | Risk scoring           | Analyze transaction patterns                 |

---

## Reconciliation Worker

### Purpose

Periodically compare internal transaction state vs. external provider state to detect discrepancies.

### Architecture

```
ReconciliationScheduler
└─ @Scheduled(cron = "0 0 */6 * * *")  [Every 6 hours]
└─ @SchedulerLock (ShedLock)
   └─ Ensures single execution across cluster

   Spring Batch Job:
   ├─ TransactionItemReader
   │  └─ Read all transactions with status='PENDING'
   │     (SELECT * FROM transactions WHERE status='PENDING' AND updated_at >= ?)
   │     Fetch in batches of 100
   │
   ├─ TransactionItemProcessor
   │  ├─ For each transaction:
   │  │  ├─ Fetch internal status: txn.status (from DB)
   │  │  ├─ Fetch external status: provider.fetchStatus(txn.providerRefId)
   │  │  ├─ Compare: internal vs. external
   │  │  │
   │  │  └─ If MISMATCH:
   │  │     └─ Create ReconciliationMismatch record
   │  │        ├─ transactionId
   │  │        ├─ internalStatus
   │  │        ├─ externalStatus
   │  │        └─ resolutionStatus = 'PENDING_INVESTIGATION'
   │  │
   │  └─ Return: ReconciliationMismatch (if mismatch) or null
   │
   ├─ TransactionItemWriter
   │  └─ Batch write ReconciliationMismatch records
   │     INSERT INTO reconciliation_mismatches (...)
   │
   └─ Completion:
      └─ Log: "Reconciliation complete: 2,345 scanned, 12 mismatches found"
```

### Mismatch Detection Examples

| Internal Status | External Status | Issue                                                      | Resolution                          |
| --------------- | --------------- | ---------------------------------------------------------- | ----------------------------------- |
| PENDING         | SUCCESS         | Payment succeeded at provider but not recorded locally     | Auto-update to SUCCESS              |
| SUCCESS         | FAILED          | Payment failed at provider but recorded as success locally | Fraud/error detected; manual review |
| FAILED          | PENDING         | Marked as failed locally but still processing              | Retry external fetch or wait        |
| SUCCESS         | SUCCESS         | Synchronized                                               | No action                           |

### Manual Resolution Workflow

```
1. Reconciliation job finds mismatch
   └─ INSERT INTO reconciliation_mismatches (txn_id, internal=PENDING, external=SUCCESS)

2. Operations team investigates via admin dashboard
   ├─ Query: SELECT * FROM reconciliation_mismatches WHERE resolution_status='PENDING_INVESTIGATION'
   ├─ Review transaction details & provider logs
   └─ Determine root cause (network timeout, duplicate charge, etc.)

3. Team initiates resolution
   ├─ Case 1: External was correct → UPDATE transactions SET status='SUCCESS' WHERE id=...
   ├─ Case 2: Internal was correct → Contact provider to reverse charge
   └─ Case 3: Duplicate detected → Issue refund + lock transaction for audit

4. Update resolution status
   └─ UPDATE reconciliation_mismatches SET resolution_status='MANUAL_RESOLVED'
```

---

## Configuration & Environment

### Application Properties (application.yml)

```yaml
spring:
  application:
    name: payorch-core-orchestrator

  datasource:
    url: jdbc:postgresql://localhost:5432/payorch_db
    username: payorch_user
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  jpa:
    hibernate:
      ddl-auto: validate  # Use Flyway migrations; don't auto-generate
    show-sql: false
    properties:
      hibernate.format_sql: true
      hibernate.dialect: org.hibernate.dialect.PostgreSQL15Dialect

  redis:
    host: localhost
    port: 6379
    database: 0

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer

  jpa:
    show-sql: false

payorch:
  idempotency:
    ttl-hours: 24

  providers:
    stripe:
      api-key: ${STRIPE_API_KEY}
      timeout-ms: 5000
    razorpay:
      api-key: ${RAZORPAY_API_KEY}
      timeout-ms: 5000

  routing:
    weight-success-rate: 0.60
    weight-latency: 0.20
    weight-cost: 0.10
    weight-circuit-breaker: 0.10

resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30000
        permitted-number-of-calls-in-half-open-state: 5
        minimum-number-of-calls: 10

    instances:
      paymentProviderCircuit-STRIPE: {}
      paymentProviderCircuit-RAZORPAY: {}

shedlock:
  defaults:
    lock-at-most-for: 10m
    lock-at-least-for: 5m
```

### Environment Variables

```bash
# Database
export DB_PASSWORD="secure_postgres_password"
export DATABASE_URL="jdbc:postgresql://localhost:5432/payorch_db"

# Redis
export REDIS_HOST="localhost"
export REDIS_PORT="6379"

# Kafka
export KAFKA_BOOTSTRAP_SERVERS="localhost:9092"

# Payment Providers
export STRIPE_API_KEY="sk_live_..."
export RAZORPAY_API_KEY="rzp_live_..."

# Java
export JAVA_OPTS="-Xmx2g -Xms2g"
```

---

## Key Algorithms & Patterns

### 1. Smart Routing Algorithm

```java
public PaymentProvider selectBestProvider(List<String> excludedProviders) {
    List<PaymentProvider> allProviders = getAvailableProviders();

    double maxScore = -1.0;
    PaymentProvider selected = null;

    for (PaymentProvider provider : allProviders) {
        if (excludedProviders.contains(provider.getId())) continue;

        PSPHealth health = metricsService.getHealth(provider.getId());
        CircuitBreakerStatus cbStatus = circuitBreakerRegistry
            .circuitBreaker("paymentProviderCircuit-" + provider.getId())
            .getState();

        // Score calculation
        double score = 0.0;
        score += health.getSuccessRate() * 0.60;  // 60% weight
        score += (1 - health.getP95Latency() / 1000.0) * 0.20;  // 20% weight
        score += (1 - provider.getCost() / 1.0) * 0.10;  // 10% weight
        score += (cbStatus == CLOSED ? 1.0 : 0.5) * 0.10;  // 10% weight

        if (score > maxScore) {
            maxScore = score;
            selected = provider;
        }
    }

    return selected;  // Highest scoring provider
}
```

### 2. Pessimistic Locking (Double-Entry Ledger)

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.id = :id")
Optional<Account> findByIdWithLock(@Param("id") UUID id);

// In LedgerService:
@Transactional(rollbackFor = Exception.class)
public void recordEntry(Transaction transaction) {
    // Locks rows in database until transaction completes
    Account sender = accountRepository.findByIdWithLock(txn.getSenderAccountId())
        .orElseThrow(() -> new AccountNotFoundException(...));
    Account receiver = accountRepository.findByIdWithLock(txn.getReceiverAccountId())
        .orElseThrow(() -> new AccountNotFoundException(...));

    // No other thread can modify these accounts until commit
    sender.setBalance(sender.getBalance().subtract(amount));
    receiver.setBalance(receiver.getBalance().add(amount));

    // Both changes committed atomically
    accountRepository.save(sender);
    accountRepository.save(receiver);
}
```

### 3. Distributed Locking (Idempotency)

```java
public boolean acquireLock(String idempotencyKey) {
    String lockKey = "lock:payment:" + idempotencyKey;

    // SET NX (only if not exists) + EX (auto-expire)
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
        lockKey,
        "PROCESSING",
        Duration.ofMinutes(5)  // Auto-expire to prevent deadlock
    );

    return Boolean.TRUE.equals(acquired);
    // Returns true if lock acquired, false if already held
}
```

### 4. Circular Failover with Recursion

```java
private ProviderResponse executeWithFailover(Transaction txn, List<String> attempted) {
    // Select next best provider (excluding tried ones)
    PaymentProvider provider = routingStrategy
        .selectBestProviderExcluding(attempted);

    String providerId = provider.getProviderId();
    attempted.add(providerId);

    try {
        // Try this provider
        CircuitBreaker cb = circuitBreakerRegistry
            .circuitBreaker("paymentProviderCircuit-" + providerId);

        ProviderResponse response = cb.executeSupplier(
            () -> provider.process(txn)
        );

        return response;  // Success

    } catch (Exception e) {
        // Check if we have more providers to try
        if (routingStrategy.getAvailableProviderCount() <= attempted.size()) {
            throw new RuntimeException("All providers exhausted", e);
        }

        // Recursively try next provider
        return executeWithFailover(txn, attempted);
    }
}
```

### 5. Write-Through Caching

```java
public String getResponse(String key) {
    // L1: Redis (fast)
    String cached = redisTemplate.opsForValue()
        .get("resp:payment:" + key);
    if (cached != null) return cached;

    // L2: Database (fallback)
    IdempotencyKey entry = repository.findById(key).orElse(null);
    if (entry != null && !isExpired(entry)) {
        // Warm L1 cache
        redisTemplate.opsForValue().set(
            "resp:payment:" + key,
            entry.getResponsePayload(),
            Duration.ofHours(24)
        );
        return entry.getResponsePayload();
    }

    return null;  // Not found
}

public void saveResponse(String key, String payload) {
    // Write to both (durability + performance)
    idempotencyKeyRepository.save(new IdempotencyKey(key, payload));
    redisTemplate.opsForValue().set(
        "resp:payment:" + key,
        payload,
        Duration.ofHours(24)
    );
}
```

---

## Common Development Tasks

### Adding a New Payment Provider

**Steps:**

1. Create `StripePaymentProvider` class implementing `PaymentProvider` interface
2. Implement `process(Transaction txn)` method
3. Register provider in `PaymentProviderFactory`
4. Add configuration in `application.yml`
5. Configure circuit breaker for provider
6. Test with integration tests

**Template:**

```java
@Component
public class StripePaymentProvider implements PaymentProvider {

    private final StripeClient stripeClient;

    @Override
    public String getProviderId() {
        return "STRIPE";
    }

    @Override
    public ProviderResponse process(Transaction transaction) {
        try {
            StripeChargeResponse response = stripeClient.createCharge(
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getIdempotencyKey()
            );

            return ProviderResponse.builder()
                .status(response.isSucceeded() ? SUCCESS : FAILED)
                .externalRefId(response.getChargeId())
                .transactionId(transaction.getId())
                .build();

        } catch (StripeException e) {
            log.error("Stripe error", e);
            throw new ProviderException(e);
        }
    }

    @Override
    public ProviderTransactionDetails fetchStatus(String externalRefId) {
        StripeCharge charge = stripeClient.retrieveCharge(externalRefId);
        return ProviderTransactionDetails.builder()
            .externalRefId(externalRefId)
            .status(charge.getStatus())
            .timestamp(charge.getCreated())
            .build();
    }
}
```

### Adding a New Database Migration

**Steps:**

1. Create `VN__description.sql` in `core-orchestrator/src/main/resources/db/migration/`
2. Use existing migrations as template
3. Test: `mvn clean install`
4. Deploy: Flyway auto-runs on application startup

**Template (V6\_\_add_merchant_table.sql):**

```sql
-- =========================================================================
-- FLYWAY MIGRATION: V6__add_merchant_table.sql
-- DESCRIPTION: Adds merchant account and profile management
-- =========================================================================

CREATE TABLE merchants (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    api_key VARCHAR(255) NOT NULL UNIQUE,
    webhook_url TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_merchants_api_key ON merchants(api_key);
CREATE INDEX idx_merchants_email ON merchants(email);
```

### Running the Application

**Development:**

```bash
cd core-orchestrator
mvn spring-boot:run
# Starts on http://localhost:8080
```

**Testing:**

```bash
mvn clean test
# Runs all unit tests

mvn clean verify
# Runs unit + integration tests
```

**Building Docker Image:**

```bash
mvn clean package -DskipTests
docker build -t payorch:latest .
docker run -p 8080:8080 payorch:latest
```

---

## Summary for AI Models/Coding Agents

This document contains everything needed to understand and extend PayOrch:

1. **Architecture:** Multi-module Spring Boot microservices
2. **Payment Flow:** Checkout → Orchestration → Ledger → Outbox → Kafka
3. **Resilience:** Circuit breakers + distributed failover
4. **Consistency:** Idempotency (write-through cache), Double-entry ledger, Transactional Outbox
5. **Operations:** Reconciliation batch job, Scheduled cleanup with ShedLock
6. **Configuration:** Environment-driven setup via YAML + env vars
7. **Patterns:** DDD (domain-driven), TDD (test-driven), Event-driven

Use this as your north star for all development tasks.

---

**Next Steps:**

- Read source code in `payorch-shared/src/main/java/com/payorch/` for data models
- Review `core-orchestrator/src/main/java/` for orchestration logic
- Check Kafka topic schema in `outbox/` service
- Run tests: `mvn clean test` for confidence
