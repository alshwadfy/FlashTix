# FlashTix: High-Concurrency Flash Sale Ticke ting Engine 🎟️⚡

> **Mission**: Sell a  strictly limited inventory of tickets (e.g.,  100 tickets) to thousands of concurrent user s (e.g., 5,000 users in k6) with **zero overs elling**, surviving instance crashes and brok er outages.

---

## 🏛️ System Architect ure

```
                       [ 5,000 Concu rrent Users ]
                                    │
                                   � �
                   [ Nginx: LB + Rate Limit  + WS ]
                                   � �
                 ┌───────� �─────────┼────� �────────────┐
                  ▼                 ▼                  ▼
          [ App Node 1 ]    [ App  Node 2 ]    [ App Node 3 ]
                  │                 │                 │
                  └────────� �────────┼─────� �───────────┘
                                    │
                ┌─────────────� ��─────┴────────� ��──────────┐
                ▼                                        ▼
    [ Redis: Atomic Gatekeeper ]            [ PostgreSQL: Source of Truth ]
    - Lu a Script: DECR stock               - PENDING  orders
    - Idempotency key tracking              - CONFIRMED tickets
    - Pub/Sub for WS  updates               - Strict relational in tegrity
               │
               ▼ 
    [ RabbitMQ: Work Engine ]
    - Queue: ` order.process`
    - Retry backoff + Dead Let ter Queue (DLQ)
               │
                ▼
    [ Payment Worker ]
    - Simulat es success / random failure
    - Success ->  Updates Postgres to CONFIRMED
    - Failure - > Compensates Redis (INCR stock)
                │
               ▼
    [ Kafka: Immuta ble Event Stream ]
    - Topic: `order-events `
    - Replayable event log
               � ��
               ▼
    [ Analytics & Audit  Consumer ]
```

---

## 🗺️ Project Mile stones

### 📍 Milestone 1: The Core Gateke eper & Oversell Prevention ✅ [COMPLETED]
*  **Goal**: Build the core order ingestion flow  with Redis and PostgreSQL; prove zero overse lling under a massive k6 barrage.
* **Key Con cepts**:
  - Why standard database queries (` SELECT ... FOR UPDATE` or optimistic locking)  buckle under 5,000 RPS.
  - Redis single-thr eaded execution & Lua script atomicity (`CHEC K_AND_DECR`).
  - Request Idempotency keys (` setIfAbsent` in Redis + `UNIQUE` index in Pos tgreSQL).
  - SAGA Compensation: Restoring in ventory if database writes fail.
* **Verifica tion Proof**:
  - Full benchmark report and n umbers: [docs/MILESTONE_1_REPORT.md](docs/MIL ESTONE_1_REPORT.md)
  - **k6 Results**: 5,000  requests, 100 HTTP 201s, 4,900 HTTP 409s, 0  unexpected errors, exact 0 oversold tickets.
 
---

### 📍 Milestone 2: High Availability  & Load Balancing ✅ [COMPLETED]
* **Goal**:  Scale Spring Boot to 3 instances behind an N ginx reverse proxy; verify zero downtime when  an instance dies.
* **Key Concepts Mastered* *:
  - Nginx upstream load balancing algorith ms (`least_conn`).
  - Transparent failover:  `proxy_next_upstream error timeout http_502 h ttp_503 non_idempotent`.
  - Resilience again st mid-flight container crashes.
* **Verifica tion Proof**:
  - Full benchmark & chaos repo rt: [docs/MILESTONE_2_REPORT.md](docs/MILESTO NE_2_REPORT.md)
  - **Chaos Test Results**: R an 5,000 requests into Nginx while killing `f lashtix-app-2` mid-flight.
  - **Outcome**: E xactly 100 orders created, 4,900 rejected, ** 0 dropped requests / 502 errors**, 100% avail ability!

---

### 📍 Milestone 3: Asynchro nous Workflows & Resilient Workers ✅ [COMPL ETED]
* **Goal**: Decouple payment processing  via RabbitMQ with automated retries, DLQ, an d SAGA stock compensation.
* **Key Concepts M astered**:
  - Task queues vs. event logs: Ra bbitMQ for transactional job dispatch and ack nowledgement.
  - At-least-once delivery, non -blocking ingestion, and consumer-side idempo tency.
  - Exponential backoff retry policies  and Dead Letter Exchanges (`order.dlx`).
  -  Distributed Sagas / Compensation: Restoring  Redis inventory if payment permanently fails. 
* **Verification Proof**:
  - Full benchmark  & SAGA report: [docs/MILESTONE_3_REPORT.md]( docs/MILESTONE_3_REPORT.md)
  - **Results**:  Handled concurrent burst with 20% simulated p ayment failures; all failed orders retried 3  times, routed into `order.dlq`, marked `FAILE D`, and triggered SAGA inventory compensation  back to Redis.
  - **Outcome**: Exactly 100  tickets confirmed sold, 19 dead orders safely  audited in DLQ, and **exact 0 final Redis st ock (zero overselling / zero lost inventory)* *!

---

### 📍 Milestone 4: Event Sourcing  & Analytics Audit Log
* **Goal**: Publish im mutable business events to Apache Kafka for a udit logging and replayable stream analytics. 
* **Key Concepts**:
  - The distinction: Rab bitMQ (ephemeral work queue, destructive read s) vs Kafka (persistent, partitioned, replaya ble append-only commit log).
  - Transactiona l Outbox Pattern vs dual-write mitigation.
   - Consumer offset management and partition ke ys.
* **Proof / Definition of Done**:
  - All  `ORDER_CREATED`, `ORDER_PAID`, and `ORDER_FA ILED` events stream into Kafka.
  - An indepe ndent analytics service replays the entire ev ent log from offset 0 and reconstructs the ex act state.

---

### 📍 Milestone 5: Real-T ime Inventory via GraphQL Subscriptions
* **G oal**: Provide real-time stock dashboards to  clients using GraphQL subscriptions backed by  Redis Pub/Sub.
* **Key Concepts**:
  - Graph QL schema definition (Queries, Mutations, Sub scriptions).
  - WebSockets across multiple l oad-balanced application instances.
  - Redis  Pub/Sub as the cross-instance WebSocket mess age bus.
* **Proof / Definition of Done**:
   - Browser/client connects to GraphQL subscrip tion `stockUpdated(eventId)`.
  - As k6 purch ases tickets, real-time ticket decrement upda tes stream to the client instantly.

---

##  🛠️ Tech Stack Matrix
| Component | Techn ology | Purpose |
|---|---|---|
| **App Runti me** | Java 26 / Spring Boot | REST APIs, bus iness rules, worker consumers |
| **Edge & Pr oxy** | Nginx | Load balancer, rate limiting,  WS proxy |
| **In-Memory Guard** | Redis | A tomic Lua stock decrement, idempotency cache,  Pub/Sub |
| **Primary Database** | PostgreSQ L | Relational ACID store of record (orders,  customers, events) |
| **Job Queue** | Rabbit MQ | Resilient worker dispatch with ACK/NACK,  retries, DLQ |
| **Event Log** | Apache Kafk a | Immutable event stream for audit trails &  analytics replay |
| **API Layer** | Spring  for GraphQL | Flexible queries & WebSocket re al-time subscriptions |
| **Orchestration** |  Docker Compose | Reproducible local cluster  (`docker compose up`) |
| **Load Testing** |  k6 | High-throughput concurrent load & overse ll verification |
  
---

## 🚀 Running Locally with Docker Compose

Spin up the entire cluster (PostgreSQL, Redis, RabbitMQ, 3 Spring Boot instances, and Nginx reverse proxy):

```bash
# 1. Clone repository
git clone https://github.com/alshwadfy/FlashTix.git
cd FlashTix

# 2. Start all services
docker compose up --build -d

# 3. Verify health
docker compose ps
```

- **Load Balanced Gateway (Nginx)**: `http://localhost:80`
- **RabbitMQ Management Dashboard**: `http://localhost:15672` (guest / guest)
- **PostgreSQL**: `localhost:5432` (user / user, database: `flashtix`)
- **Redis**: `localhost:6379`

### Verification & Chaos Testing (k6)

Run the automated k6 load and chaos test suites:

```bash
# Milestone 1: Concurrent flash sale simulation (5,000 requests, 100 stock)
k6 run k6/flash_sale_test.js

# Milestone 2: Chaos engineering failover test (killing app node mid-flight)
k6 run k6/chaos_failover_test.js

# Milestone 3: Asynchronous workers, DLQ, and SAGA compensation test
k6 run k6/rabbitmq_saga_test.js
```

### Detailed Milestone Verification Reports
- [Milestone 1 Benchmark Report: Redis Lua & Zero Oversell](docs/MILESTONE_1_REPORT.md)
- [Milestone 2 Chaos Report: Nginx HA & Failover](docs/MILESTONE_2_REPORT.md)
- [Milestone 3 Asynchronous Workers & SAGA Compensation](docs/MILESTONE_3_REPORT.md)