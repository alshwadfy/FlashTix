# FlashTix: High-Concurrency Flash Sale Ticketing Engine 🎟️⚡

> **Mission**: Sell a strictly limited inventory of tickets (e.g., 100 tickets) to thousands of concurrent users (e.g., 5,000 users in k6) with **zero overselling**, surviving instance crashes and broker outages.

---

## 🏛️ System Architecture

```
                       [ 5,000 Concurrent Users ]
                                   │
                                   ▼
                   [ Nginx: LB + Rate Limit + WS ]
                                   │
                 ┌─────────────────┼─────────────────┐
                 ▼                 ▼                 ▼
          [ App Node 1 ]    [ App Node 2 ]    [ App Node 3 ]
                 │                 │                 │
                 └─────────────────┼─────────────────┘
                                   │
               ┌───────────────────┴───────────────────┐
               ▼                                       ▼
    [ Redis: Atomic Gatekeeper ]           [ PostgreSQL: Source of Truth ]
    - Lua Script: DECR stock               - PENDING orders
    - Idempotency key tracking             - CONFIRMED tickets
    - Pub/Sub for WS updates               - Strict relational integrity
               │
               ▼
    [ RabbitMQ: Work Engine ]
    - Queue: `order.process`
    - Retry backoff + Dead Letter Queue (DLQ)
               │
               ▼
    [ Payment Worker ]
    - Simulates success / random failure
    - Success -> Updates Postgres to CONFIRMED
    - Failure -> Compensates Redis (INCR stock)
               │
               ▼
    [ Kafka: Immutable Event Stream ]
    - Topic: `order-events`
    - Replayable event log
               │
               ▼
    [ Analytics & Audit Consumer ]
```

---

## 🗺️ Project Milestones

### 📍 Milestone 1: The Core Gatekeeper & Oversell Prevention ✅ [COMPLETED]
* **Goal**: Build the core order ingestion flow with Redis and PostgreSQL; prove zero overselling under a massive k6 barrage.
* **Key Concepts**:
  - Why standard database queries (`SELECT ... FOR UPDATE` or optimistic locking) buckle under 5,000 RPS.
  - Redis single-threaded execution & Lua script atomicity (`CHECK_AND_DECR`).
  - Request Idempotency keys (`setIfAbsent` in Redis + `UNIQUE` index in PostgreSQL).
  - SAGA Compensation: Restoring inventory if database writes fail.
* **Verification Proof**:
  - Full benchmark report and numbers: [docs/MILESTONE_1_REPORT.md](docs/MILESTONE_1_REPORT.md)
  - **k6 Results**: 5,000 requests, 100 HTTP 201s, 4,900 HTTP 409s, 0 unexpected errors, exact 0 oversold tickets.

---

### 📍 Milestone 2: High Availability & Load Balancing
* **Goal**: Scale Spring Boot to 3 instances behind an Nginx reverse proxy; verify zero downtime when an instance dies.
* **Key Concepts**:
  - Nginx upstream load balancing algorithms (Round Robin / Least Conn).
  - Rate limiting bursts at the edge to protect downstream services.
  - Graceful degradation and connection handling.
* **Proof / Definition of Done**:
  - Run the k6 load test while abruptly killing one of the three Spring Boot containers (`docker stop app-2`).
  - Traffic seamlessly routes to surviving nodes without 502 Bad Gateway errors or lost orders.

---

### 📍 Milestone 3: Asynchronous Workflows & Resilient Workers
* **Goal**: Decouple payment processing via RabbitMQ with automated retries, DLQ, and stock compensation.
* **Key Concepts**:
  - Task queues vs. event logs: Why RabbitMQ is designed for transactional job dispatch and acknowledgement.
  - At-least-once delivery and consumer-side idempotency.
  - Exponential backoff retry policies and Dead Letter Exchanges (`order.dlx`).
  - Distributed Sagas / Compensation: Restoring Redis inventory if payment permanently fails.
* **Proof / Definition of Done**:
  - Inject 20% simulated payment failures.
  - All failed tickets are safely credited back to Redis and snapped up by waiting users.
  - Exhausted retries land safely in the DLQ without crashing the queue.

---

### 📍 Milestone 4: Event Sourcing & Analytics Audit Log
* **Goal**: Publish immutable business events to Apache Kafka for audit logging and replayable stream analytics.
* **Key Concepts**:
  - The distinction: RabbitMQ (ephemeral work queue, destructive reads) vs Kafka (persistent, partitioned, replayable append-only commit log).
  - Transactional Outbox Pattern vs dual-write mitigation.
  - Consumer offset management and partition keys.
* **Proof / Definition of Done**:
  - All `ORDER_CREATED`, `ORDER_PAID`, and `ORDER_FAILED` events stream into Kafka.
  - An independent analytics service replays the entire event log from offset 0 and reconstructs the exact state.

---

### 📍 Milestone 5: Real-Time Inventory via GraphQL Subscriptions
* **Goal**: Provide real-time stock dashboards to clients using GraphQL subscriptions backed by Redis Pub/Sub.
* **Key Concepts**:
  - GraphQL schema definition (Queries, Mutations, Subscriptions).
  - WebSockets across multiple load-balanced application instances.
  - Redis Pub/Sub as the cross-instance WebSocket message bus.
* **Proof / Definition of Done**:
  - Browser/client connects to GraphQL subscription `stockUpdated(eventId)`.
  - As k6 purchases tickets, real-time ticket decrement updates stream to the client instantly.

---

## 🛠️ Tech Stack Matrix
| Component | Technology | Purpose |
|---|---|---|
| **App Runtime** | Java 26 / Spring Boot | REST APIs, business rules, worker consumers |
| **Edge & Proxy** | Nginx | Load balancer, rate limiting, WS proxy |
| **In-Memory Guard** | Redis | Atomic Lua stock decrement, idempotency cache, Pub/Sub |
| **Primary Database** | PostgreSQL | Relational ACID store of record (orders, customers, events) |
| **Job Queue** | RabbitMQ | Resilient worker dispatch with ACK/NACK, retries, DLQ |
| **Event Log** | Apache Kafka | Immutable event stream for audit trails & analytics replay |
| **API Layer** | Spring for GraphQL | Flexible queries & WebSocket real-time subscriptions |
| **Orchestration** | Docker Compose | Reproducible local cluster (`docker compose up`) |
| **Load Testing** | k6 | High-throughput concurrent load & oversell verification |
