# PayOrch System Diagrams & Visual Documentation

---

## 1. Request Flow Diagram (End-to-End)

```
CLIENT LAYER
    │
    ├─ POST /checkout
    │  Headers: X-Idempotency-Key
    │  Body: {amount, currency, senderAccount, receiverAccount}
    │
    ▼
CORE ORCHESTRATOR (Port 8080)
    │
    ┌─────────────────────────────────────────┐
    │ CheckoutController                      │
    │  └─ Validate request                    │
    │  └─ Create Transaction entity           │
    └────────────┬────────────────────────────┘
                 │
                 ▼
    ┌─────────────────────────────────────────────┐
    │ PaymentOrchestrator.processPayment()       │
    │                                             │
    │ 1. IdempotencyManager.getResponse()        │
    │    ├─ Redis cache? → RETURN (replay)       │
    │    └─ DB cache? → Warm Redis → RETURN      │
    │                                             │
    │ 2. IdempotencyManager.acquireLock()        │
    │    └─ Distributed lock (Redis, 5m TTL)    │
    │                                             │
    │ 3. SmartRoutingStrategy.selectBestProvider│
    │    └─ Score: success rate, latency, cost  │
    │                                             │
    │ 4. executeWithFailover() [RECURSIVE]       │
    │    ├─ PaymentStateManager.initializeState │
    │    ├─ CircuitBreaker.executeSupplier()   │
    │    │   └─ [NETWORK CALL TO PROVIDER]      │
    │    ├─ PaymentStateManager.finalizeState  │
    │    └─ If fails → Retry with next provider │
    │                                             │
    │ 5. IdempotencyManager.saveResponse()       │
    │    ├─ Write to DB (idempotency_keys)      │
    │    ├─ Update Redis cache (24h TTL)        │
    │    └─ Release lock                         │
    └────────────┬────────────────────────────────┘
                 │
                 ▼
    ┌─────────────────────────────────────────┐
    │ HTTP 200 OK                             │
    │ {status, transactionId, externalRefId}  │
    └────────────┬────────────────────────────┘
                 │
                 ▼
    CLIENT RECEIVES RESPONSE
    (Total latency: 200-500ms)


ASYNC PROCESSING (Triggered after response)
    │
    ▼
    ┌──────────────────────────────────────────┐
    │ LedgerService.recordEntry() @Transactional│
    │                                           │
    │ 1. Fetch accounts with pessimistic lock   │
    │ 2. Validate sender balance               │
    │ 3. Modify account balances               │
    │ 4. Create DEBIT/CREDIT ledger entries   │
    │ 5. OutboxPatternWorker.saveOutboxEvent() │
    │    └─ INSERT outbox record               │
    │ 6. COMMIT (ledger + outbox atomic)       │
    └────────────┬─────────────────────────────┘
                 │
                 ▼
    ┌──────────────────────────────────────────┐
    │ OutboxPatternWorker.publishOutboxEvent()│
    │ @Transactional(REQUIRES_NEW)             │
    │                                           │
    │ 1. Publish to Kafka: payment-events     │
    │ 2. UPDATE outbox SET status='PROCESSED' │
    └────────────┬─────────────────────────────┘
                 │
                 ▼
    KAFKA DOWNSTREAM CONSUMERS
    ├─ Notification Service (email/SMS)
    ├─ Analytics Service (metrics)
    ├─ Reporting Service (dashboards)
    ├─ Webhook Service (merchant notify)
    └─ Fraud Detection Service

PERIODIC OPERATIONS
    │
    ├─ Every 6 hours:
    │  └─ ReconciliationScheduler
    │     └─ Compare internal vs. external status
    │     └─ Create reconciliation_mismatches records
    │
    └─ Daily at 2 AM UTC:
       └─ IdempotencyCleanupScheduler
          └─ Delete expired idempotency entries
```

---

## 2. Database Entity Relationship Diagram (ERD)

```
┌──────────────────────────────────────────┐
│           accounts                        │
├──────────────────────────────────────────┤
│ id (UUID) PRIMARY KEY                    │
│ owner_id (VARCHAR)                       │
│ balance (DECIMAL)                        │
│ currency (VARCHAR)                       │
│ version (BIGINT) [Optimistic Lock]      │
│ created_at (TIMESTAMP)                   │
└──────────────────────────────────────────┘
           ▲                    ▲
           │ 1                  │ 1
           │                    │
         (FK)                 (FK)
           │                    │
           │                    │
┌──────────┴────────────────────┴──────────┐
│         transactions                      │
├───────────────────────────────────────────┤
│ id (UUID) PRIMARY KEY                    │
│ idempotency_key (VARCHAR) UNIQUE         │
│ amount (DECIMAL)                         │
│ currency (VARCHAR)                       │
│ status (VARCHAR) [INITIATED, PENDING...] │
│ provider_id (VARCHAR)                    │
│ provider_ref_id (VARCHAR)                │
│ sender_account_id (UUID) → accounts.id  │
│ receiver_account_id (UUID) → accounts.id│
│ created_at (TIMESTAMP)                   │
│ updated_at (TIMESTAMP)                   │
└───────────────────┬────────────────────────┘
                    │ (FK)
                    │ (1 to many)
                    │
        ┌───────────▼──────────────────┐
        │   ledger_entries             │
        ├──────────────────────────────┤
        │ id (UUID) PRIMARY KEY        │
        │ transaction_id (UUID) → txn │
        │ account_id (UUID) → accts   │
        │ amount (DECIMAL)             │
        │ entry_type (DEBIT, CREDIT)  │
        │ created_at (TIMESTAMP)       │
        └──────────────────────────────┘

┌──────────────────────────────────────────┐
│       idempotency_keys                   │
├──────────────────────────────────────────┤
│ id (VARCHAR) PRIMARY KEY                 │
│ request_hash (VARCHAR)                   │
│ response_payload (TEXT/JSON)             │
│ created_at (TIMESTAMP)                   │
│ expires_at (TIMESTAMP)                   │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│         outbox                           │
├──────────────────────────────────────────┤
│ id (UUID) PRIMARY KEY                    │
│ event_type (VARCHAR)                     │
│ payload (JSONB)                          │
│ status (PENDING, PROCESSED, FAILED)      │
│ created_at (TIMESTAMP)                   │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│   reconciliation_mismatches              │
├──────────────────────────────────────────┤
│ id (UUID) PRIMARY KEY                    │
│ transaction_id (UUID) → transactions.id │
│ provider_ref_id (VARCHAR)                │
│ internal_status (VARCHAR)                │
│ external_status (VARCHAR)                │
│ resolution_status (VARCHAR)              │
│ created_at (TIMESTAMP)                   │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│          shedlock                        │
├──────────────────────────────────────────┤
│ name (VARCHAR) PRIMARY KEY               │
│ lock_until (TIMESTAMP)                   │
│ locked_at (TIMESTAMP)                    │
│ locked_by (VARCHAR)                      │
└──────────────────────────────────────────┘
```

---

## 3. Microservices Communication Diagram

```
┌─────────────────────────────────────────────────────────┐
│ CLIENT APPLICATION                                       │
│ (Mobile App / Web / REST Client)                        │
└──────────────┬──────────────────────────────────────────┘
               │ HTTP REST
               │ POST /checkout
               │ Headers: X-Idempotency-Key
               │
               ▼
        ┌──────────────────────────────────────┐
        │  CORE ORCHESTRATOR SERVICE           │
        │  (Port 8080)                         │
        │                                       │
        │  Responsibilities:                   │
        │  - Payment orchestration             │
        │  - Ledger management                 │
        │  - Idempotency handling              │
        │  - Provider selection & failover     │
        └──────┬───────────┬────────┬──────────┘
               │           │        │
               │           │        └─── Metrics Service
               │           │            └─ Health scoring
               │           │
        ┌──────▼──────┐    └─────────► SmartRoutingStrategy
        │              │                 └─ Provider scoring
        │              │
    ┌───▼──────────────▼────┐
    │ External Payment       │
    │ Providers              │ (Network calls with circuit breakers)
    │                        │
    │ ├─ Stripe             │
    │ ├─ Razorpay           │
    │ └─ PayPal             │
    └────────────────────────┘

        ┌──────────────────────────────────────┐
        │  WEBHOOK WORKER SERVICE              │
        │  (Port 8081)                         │
        │                                       │
        │  Responsibilities:                   │
        │  - Receive provider webhooks         │
        │  - Update transaction status         │
        │  - Publish to Kafka                  │
        └──────────────────────────────────────┘
               ▲
               │ Webhooks from providers
               │ POST /webhook/{provider}
               │

        ┌──────────────────────────────────────┐
        │  RECONCILIATION WORKER SERVICE       │
        │  (Batch Jobs)                        │
        │                                       │
        │  Responsibilities:                   │
        │  - Scheduled reconciliation (6h)     │
        │  - Compare internal vs external      │
        │  - Create mismatch records           │
        │  - Cleanup expired idempotency (24h) │
        └──────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ SHARED INFRASTRUCTURE                                    │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │ PostgreSQL Database (Port 5432)                  │   │
│  │ - Accounts, Transactions, Ledger                │   │
│  │ - Idempotency cache (L2)                        │   │
│  │ - Outbox events, Reconciliation mismatches      │   │
│  │ - Flyway schema migrations                      │   │
│  └─────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Redis Cache (Port 6379)                         │   │
│  │ - Idempotency response cache (L1, <1ms)        │   │
│  │ - Distributed locks (payment processing)        │   │
│  │ - Provider health metrics                       │   │
│  │ - Session data                                  │   │
│  └─────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Apache Kafka (Port 9092)                        │   │
│  │ - Topic: payment-events                         │   │
│  │   ├─ Consumers: Notification, Analytics         │   │
│  │   ├─ Consumers: Reporting, Webhook              │   │
│  │   └─ Consumers: Fraud Detection                 │   │
│  └─────────────────────────────────────────────────┘   │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 4. Idempotency Caching Strategy

```
                    IDEMPOTENT REQUEST
                    X-Idempotency-Key: "abc-123"
                             │
                             ▼
                   ┌─────────────────────┐
                   │  IdempotencyManager  │
                   │  .getResponse()      │
                   └──────────┬───────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
   ┌─────────────┐       ┌─────────────┐      NO CACHE
   │   Redis     │       │ PostgreSQL  │     (First Request)
   │   L1 Cache  │       │   L2 Cache  │
   │             │       │             │
   │ GET key     │       │ SELECT FROM │
   │             │       │ idempotency │
   │  <1ms       │       │   _keys     │
   │             │       │             │
   │             │       │   ~10ms     │
   └────┬────────┘       └──────┬──────┘
        │                       │
        │ HIT                   │ HIT
        │                       │
        ├─────────────┬─────────┘
                      │
                      ▼
            ┌──────────────────────┐
            │ Return Cached        │
            │ ProviderResponse     │
            │ (Immediate to client)│
            │                      │
            │ NO PAYMENT EXECUTED  │
            │ Exact replay!        │
            └──────────────────────┘


WRITE-THROUGH ON EXECUTION:

    Execute Payment (First Request Only)
              │
              ▼
    ProviderResponse: SUCCESS
              │
              ▼
    IdempotencyManager.saveResponse()
              │
        ┌─────┴─────┐
        │           │
        ▼           ▼
    PostgreSQL   Redis
    WRITE        WRITE
    (Durability) (Performance)
        │           │
        └─────┬─────┘
              │
              ▼
    Both caches synchronized:
    - idempotency_keys.response_payload
    - resp:payment:abc-123 (Redis)
    - Both: 24-hour TTL
```

---

## 5. Ledger Double-Entry Pattern

```
ACCOUNT A (Sender)          ACCOUNT B (Receiver)
┌─────────────────┐         ┌──────────────────┐
│ Balance: $5,000 │         │ Balance: $3,000  │
└────────┬────────┘         └────────┬─────────┘
         │                           │
         │ -$100 (DEBIT)             │ +$100 (CREDIT)
         │                           │
         ▼                           ▼
    ┌──────────────────────────────────────┐
    │  TRANSACTION                         │
    │  ID: 550e8400-e29b-41d4...          │
    │  Amount: $100                        │
    │  Status: SUCCESS                     │
    └──────────────────────────────────────┘
         │                           │
         ▼                           ▼
    ┌──────────────────┐         ┌──────────────────┐
    │ LEDGER ENTRY     │         │ LEDGER ENTRY     │
    │ ├─ Type: DEBIT   │         │ ├─ Type: CREDIT  │
    │ ├─ Account: A    │         │ ├─ Account: B    │
    │ ├─ Amount: -100  │         │ ├─ Amount: +100  │
    │ └─ Txn ID: 550.. │         │ └─ Txn ID: 550.. │
    └──────────────────┘         └──────────────────┘
         │                           │
         ▼                           ▼
    Account A Balance           Account B Balance
    = 5000 - 100                = 3000 + 100
    = 4900                      = 3100

    INVARIANT: Sum of all entries = 0 (Balanced)
    DEBIT (-100) + CREDIT (+100) = 0 ✓

    AUDIT TRAIL: All entries timestamped & immutable
    ROLLBACK: All entries created in single @Transactional
```

---

## 6. Circuit Breaker State Machine

```
┌────────────────────────────────────────────────────────────┐
│                  CLOSED (Normal)                           │
│                  ├─ All requests pass through              │
│                  ├─ Count failures                         │
│                  └─ Calculate failure rate: F / (F + S)   │
│                     (Failures / Total)                     │
└─────────────────────┬──────────────────────────────────────┘
                      │
                      │ Condition: failure_rate > 50%
                      │            AND minimum_calls > 10
                      ▼
┌────────────────────────────────────────────────────────────┐
│                   OPEN (Circuit Broken)                    │
│                   ├─ All requests rejected immediately     │
│                   ├─ No calls to failing provider          │
│                   ├─ Start timer: wait_duration = 30s     │
│                   └─ Prevents cascading failure            │
└─────────────────────┬──────────────────────────────────────┘
                      │
                      │ After 30 seconds elapsed
                      ▼
┌────────────────────────────────────────────────────────────┐
│              HALF_OPEN (Testing Recovery)                  │
│              ├─ Allow limited requests (5 calls)           │
│              ├─ Monitor if provider has recovered          │
│              └─ If failure → back to OPEN                 │
│                If success → go to CLOSED                  │
└─────────────────────┬──────────────────────────────────────┘
                      │
         ┌────────────┴────────────┐
         │                         │
    Success? YES              Success? NO
         │                         │
         ▼                         ▼
      CLOSED ◄──────────────────OPEN
   (recovered)            (still broken)


EXAMPLE TIMELINE:

T=0   : STRIPE success rate: 98% (CLOSED)
T=100 : STRIPE API down, failure rate: 60% (OPEN)
T=200 : Requests rejected with open circuit
T=300 : Requests rejected with open circuit
...
T=3000: 30 seconds elapsed → Test recovery (HALF_OPEN)
T=3100: 5 test requests → 4 succeed → 1 fails
T=3200: Went back to OPEN (not recovered)
T=3300: Wait another 30s
T=6200: HALF_OPEN again → All 5 succeed → Back to CLOSED
T=6300: Resume normal traffic to STRIPE
```

---

## 7. Distributed Failover Logic

```
INITIATE FAILOVER
       │
       ▼
┌──────────────────────────────────┐
│ executeWithFailover()            │
│ (Called with attempted=[])       │
└──────────────┬───────────────────┘
               │
               ▼
    ┌─────────────────────────────┐
    │ SmartRoutingStrategy        │
    │ .selectBestProviderExcl()   │
    │ (Excluding: [])             │
    │                             │
    │ Available providers:        │
    │ ├─ STRIPE (Score: 0.96)    │
    │ ├─ RAZORPAY (Score: 0.82)  │
    │ └─ PAYPAL (Score: 0.45)    │
    │                             │
    │ Selected: STRIPE            │
    └──────────┬──────────────────┘
               │
               ▼ attempted.add("STRIPE")
    ┌─────────────────────────────┐
    │ Try: CircuitBreaker.execute │
    │      stripe.process(txn)    │
    └──────────┬──────────────────┘
               │
         FAILURE!
    (Network timeout)
               │
               ▼
    ┌──────────────────────────────────────┐
    │ Catch exception                      │
    │ Check: available_count (3)          │
    │        vs attempted.size (1)        │
    │ 3 > 1? YES                          │
    │ → Continue to next provider          │
    └──────────────┬─────────────────────────┘
                   │
                   ▼
         executeWithFailover()
         (Recursive call with
          attempted=["STRIPE"])
                   │
                   ▼
    ┌─────────────────────────────┐
    │ SmartRoutingStrategy        │
    │ .selectBestProviderExcl()   │
    │ (Excluding: ["STRIPE"])     │
    │                             │
    │ Selected: RAZORPAY          │
    └──────────┬──────────────────┘
               │
               ▼ attempted.add("RAZORPAY")
    ┌─────────────────────────────┐
    │ Try: CircuitBreaker.execute │
    │      razorpay.process(txn)  │
    └──────────┬──────────────────┘
               │
         SUCCESS!
               │
               ▼
    ┌─────────────────────────────┐
    │ Return ProviderResponse     │
    │ to PaymentOrchestrator      │
    └──────────────────────────────┘
```

---

## 8. Reconciliation Batch Job Flow

```
ReconciliationScheduler
    @Scheduled(cron = "0 0 */6 * * *")  [Every 6 hours]
    @SchedulerLock (Cluster-safe)
           │
           ▼
    Spring Batch Job
           │
    ┌──────┴───────────────────────────────┐
    │       TransactionItemReader          │
    │                                      │
    │ JpaPagingItemReader                  │
    │ Query: SELECT * FROM transactions    │
    │        WHERE status = 'PENDING'      │
    │        AND updated_at >= lookback    │
    │                                      │
    │ Chunk size: 100 items                │
    │ Pagination: Page 1, 2, 3...          │
    └──────┬───────────────────────────────┘
           │
           ▼ [100 Transaction items per page]
    ┌──────────────────────────────────────┐
    │   TransactionItemProcessor           │
    │                                      │
    │   For each transaction:              │
    │   1. Fetch internal status (our DB)  │
    │   2. Fetch external status (provider)│
    │   3. Compare                         │
    │                                      │
    │   If MISMATCH:                       │
    │   └─ Return ReconciliationMismatch   │
    │   Else:                              │
    │   └─ Return null (skip writing)      │
    │                                      │
    │   Mismatch: {                        │
    │     txn_id: ...,                     │
    │     internal: "PENDING",             │
    │     external: "SUCCESS",             │
    │     resolution: "PENDING_..."        │
    │   }                                  │
    └──────┬───────────────────────────────┘
           │
           ▼ [Write batch]
    ┌──────────────────────────────────────┐
    │   TransactionItemWriter              │
    │                                      │
    │ INSERT INTO reconciliation_mismatches│
    │ (transaction_id, internal_status,   │
    │  external_status, resolution_status)│
    │                                      │
    │ Batch size: 100 records per commit  │
    └──────┬───────────────────────────────┘
           │
           ▼ [Repeat for next page]
    [More pages? → Loop back to Reader]
           │
           ▼ [All pages processed]
    ┌──────────────────────────────────────┐
    │ Batch Job Completion                 │
    │ Log: "Reconciliation complete:       │
    │       2,345 scanned,                 │
    │       12 mismatches found"           │
    └──────────────────────────────────────┘
```

---

## 9. Configuration & Environment Variables

```
APPLICATION CONFIGURATION
└─ application.yml (checked into git)
   └─ Contains default values
   └─ Overrideable via env vars

RUNTIME ENVIRONMENT VARIABLES
├─ Database
│  ├─ DB_PASSWORD
│  ├─ DATABASE_URL
│  └─ Default: localhost:5432
│
├─ Redis
│  ├─ REDIS_HOST (default: localhost)
│  ├─ REDIS_PORT (default: 6379)
│  └─ REDIS_PASSWORD (optional)
│
├─ Kafka
│  └─ KAFKA_BOOTSTRAP_SERVERS (default: localhost:9092)
│
├─ Payment Providers
│  ├─ STRIPE_API_KEY
│  ├─ RAZORPAY_API_KEY
│  └─ PAYPAL_API_KEY
│
└─ Java Runtime
   ├─ JAVA_OPTS="-Xmx2g -Xms2g"
   └─ JAVA_TOOL_OPTIONS (logging config)
```

---

## 10. Data Flow: From Request to Database

```
CLIENT REQUEST
│
├─ X-Idempotency-Key: "abc-123"
├─ Body: {amount: 100, currency: USD, ...}
│
▼
HTTP POST /checkout
│
▼
CheckoutController
│
├─ Parse: @RequestBody CheckoutRequest
├─ Validate: Not null, amount > 0
├─ Create: Transaction entity
│  └─ Set status = INITIATED
│  └─ Set idempotencyKey = "abc-123"
│
▼
PaymentOrchestrator.processPayment(Transaction)
│
├─ Cache check: IdempotencyManager.getResponse("abc-123")
├─ Lock acquire: IdempotencyManager.acquireLock("abc-123")
├─ Provider select: SmartRoutingStrategy.selectBestProvider()
├─ Execute: circuitBreaker.executeSupplier(provider.process())
│
▼ SUCCESS
│
├─ Transaction.status = SUCCESS
├─ Transaction.providerRefId = "ch_1ABC..."
├─ TransactionRepository.save(transaction)
│  └─ INSERT/UPDATE transactions table
│
▼
LedgerService.recordEntry(transaction)
│
├─ Fetch: sender account (with lock)
├─ Fetch: receiver account (with lock)
├─ Modify: sender.balance -= 100
├─ Modify: receiver.balance += 100
├─ Create: LedgerEntry (DEBIT)
├─ Create: LedgerEntry (CREDIT)
├─ LedgerRepository.save(debitEntry)
│  └─ INSERT ledger_entries (DEBIT)
├─ LedgerRepository.save(creditEntry)
│  └─ INSERT ledger_entries (CREDIT)
├─ OutboxPatternWorker.saveOutboxEvent(transaction)
│  └─ INSERT outbox (event_type, payload)
│
▼ COMMIT (all changes atomically)
│
DATABASE STATE AFTER COMMIT:
├─ transactions: Updated (status=SUCCESS, provider_ref_id)
├─ accounts: Updated (both balances)
├─ ledger_entries: 2 new records (DEBIT + CREDIT)
├─ idempotency_keys: Cache saved
└─ outbox: Event queued for Kafka

▼
OutboxPatternWorker.publishOutboxEvent()
│
├─ Publish to Kafka topic: payment-events
├─ Mark outbox: status = PROCESSED
│
▼
DOWNSTREAM CONSUMERS
├─ Notification Service
├─ Analytics Service
├─ Reporting Service
├─ Webhook Service
└─ Fraud Detection
```

---

**Visual Documentation Summary:**

- **Diagram 1:** Complete end-to-end request flow with timing
- **Diagram 2:** Database schema relationships (ERD)
- **Diagram 3:** Microservices communication topology
- **Diagram 4:** Idempotency cache hierarchy (L1/L2)
- **Diagram 5:** Ledger double-entry pattern
- **Diagram 6:** Circuit breaker state machine
- **Diagram 7:** Distributed failover algorithm
- **Diagram 8:** Reconciliation batch job flow
- **Diagram 9:** Configuration & environment variables
- **Diagram 10:** Data flow from request to database

All diagrams use ASCII art for portability and can be viewed in any text editor.
