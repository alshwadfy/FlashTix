# FlashTix: High-Concurrency Flash Sale Ticke  ting Engine 🎟️⚡

> **Mission**: Sell  a  strictly limited inventory of tickets (e.g .,  100 tickets) to thousands of concurrent u ser s (e.g., 5,000 users in k6) with **zero o vers elling**, surviving instance crashes and  brok er outages.

---

## 🏛️ System Arc hitect ure

```
                       [ 5,00 0 Concu rrent Users ]
                                     │
                                    � �
                   [ Nginx: LB  + Rate Limit  + WS ]
                                    � �
                 ┌─� �─────� �───────� ��─┼────� �────── ──────┐
                  ▼                  ▼                  ▼
           [ App Node 1 ]    [ App  Node 2 ]    [ A pp Node 3 ]
                  │                  │                 │
                   └────────� �─── ─────┼─────� �─� �─────────┘
                                     │
                � �─────────────� � ���─────┴───────� ��� ��──────────┐ 
                ▼                                         ▼
    [ Redis: Atomic Gateke eper ]            [ PostgreSQL: Source of Tru th ]
    - Lu a Script: DECR stock                - PENDING  orders
    - Idempotency key t racking              - CONFIRMED tickets
     - Pub/Sub for WS  updates               - Str ict relational in tegrity
               │
                ▼ 
    [ RabbitMQ: Work Engi ne ]
    - Queue: ` order.process`
    - Retr y backoff + Dead Let ter Queue (DLQ)
                │
                ▼
    [ Payment  Worker ]
    - Simulat es success / random fa ilure
    - Success ->  Updates Postgres to C ONFIRMED
    - Failure - > Compensates Redis  (INCR stock)
                │
                ▼
    [ Kafka: Immuta ble Event Stream ] 
    - Topic: `order-events `
    - Replayabl e event log
               � ��
                ▼
    [ Analytics & Audit  Consumer  ]
```

---

## 🗺️ Project Mile stones

 ### 📍 Milestone 1: The Core Gateke eper &  Oversell Prevention ✅ [COMPLETED]
*  **Goal **: Build the core order ingestion flow  with  Redis and PostgreSQL; prove zero overse llin g under a massive k6 barrage.
* **Key Con cep ts**:
  - Why standard database queries (` SE LECT ... FOR UPDATE` or optimistic locking)   buckle under 5,000 RPS.
  - Redis single-thr  eaded execution & Lua script atomicity (`CHEC  K_AND_DECR`).
  - Request Idempotency keys ( ` setIfAbsent` in Redis + `UNIQUE` index in P os tgreSQL).
  - SAGA Compensation: Restoring  in ventory if database writes fail.
* **Veri fica tion Proof**:
  - Full benchmark report and numbers: [docs/MILESTONE_1_REPORT.md](docs/MILESTONE_1_REPORT.md)
  - **k6 Results** : 5,000  requests, 100 HTTP 201s, 4,900 HTTP  409s, 0  unexpected errors, exact 0 oversold  tickets.
 
---

### 📍 Milestone 2: High Av ailability  & Load Balancing ✅ [COMPLETED]
 * **Goal**:  Scale Spring Boot to 3 instances  behind an N ginx reverse proxy; verify zero  downtime when  an instance dies.
* **Key Conc epts Mastered* *:
  - Nginx upstream load bal ancing algorith ms (`least_conn`).
  - Transp arent failover:  `proxy_next_upstream error t imeout http_502 h ttp_503 non_idempotent`.
   - Resilience again st mid-flight container cr ashes.
* **Verifica tion Proof**:
  - Full benchmark & chaos report: [docs/MILESTONE_2_REPORT.md](docs/MILESTONE_2_REPORT.md)
  - **C haos Test Results**: R an 5,000 requests into  Nginx while killing `f lashtix-app-2` mid-fl ight.
  - **Outcome**: E xactly 100 orders cr eated, 4,900 rejected, ** 0 dropped requests  / 502 errors**, 100% avail ability!

---

###  📍 Milestone 3: Asynchro nous Workflows &  Resilient Workers ✅ [COMPL ETED]
* **Goal** : Decouple payment processing  via RabbitMQ w ith automated retries, DLQ, an d SAGA stock c ompensation.
* **Key Concepts M astered**:
   - Task queues vs. event logs: Ra bbitMQ for t ransactional job dispatch and ack nowledgemen t.
  - At-least-once delivery, non -blocking  ingestion, and consumer-side idempo tency.
   - Exponential backoff retry policies  and Dea d Letter Exchanges (`order.dlx`).
  -  Distri buted Sagas / Compensation: Restoring  Redis  inventory if payment permanently fails. 
* ** Verification Proof**:
  - Full benchmark & SAGA report: [docs/MILESTONE_3_REPORT.md](docs/MILESTONE_3_REPORT.md)
  - **Results**:  Ha ndled concurrent burst with 20% simulated p a yment failures; all failed orders retried 3   times, routed into `order.dlq`, marked `FAILE  D`, and triggered SAGA inventory compensatio n  back to Redis.
  - **Outcome**: Exactly 10 0  tickets confirmed sold, 19 dead orders saf ely  audited in DLQ, and **exact 0 final Redi s st ock (zero overselling / zero lost invent ory)* *!

---

### 📍 Milestone 4: Event So urcing  & Analytics Audit Log
* **Goal**: Pub lish im mutable business events to Apache Kaf ka for a udit logging and replayable stream a nalytics. 
* **Key Concepts**:
  - The distin ction: Rab bitMQ (ephemeral work queue, destr uctive read s) vs Kafka (persistent, partitio ned, replaya ble append-only commit log).
  -  Transactiona l Outbox Pattern vs dual-write  mitigation.
   - Consumer offset management a nd partition ke ys.
* **Proof / Definition of  Done**:
  - All  `ORDER_CREATED`, `ORDER_PAI D`, and `ORDER_FA ILED` events stream into Ka fka.
  - An indepe ndent analytics service re plays the entire ev ent log from offset 0 and  reconstructs the ex act state.

---

### � � Milestone 5: Real-T ime Inventory via Graph QL Subscriptions
* **G oal**: Provide real-ti me stock dashboards to  clients using GraphQL  subscriptions backed by  Redis Pub/Sub.
* ** Key Concepts**:
  - Graph QL schema definitio n (Queries, Mutations, Sub scriptions).
  - W ebSockets across multiple l oad-balanced appl ication instances.
  - Redis  Pub/Sub as the  cross-instance WebSocket mess age bus.
* **Pr oof / Definition of Done**:
   - Browser/clie nt connects to GraphQL subscrip tion `stockUp dated(eventId)`.
  - As k6 purch ases tickets , real-time ticket decrement upda tes stream  to the client instantly.

---

##  🛠️ Te ch Stack Matrix
| Component | Techn ology | P urpose |
|---|---|---|
| **App Runti me** | J ava 26 / Spring Boot | REST APIs, bus iness r ules, worker consumers |
| **Edge & Pr oxy**  | Nginx | Load balancer, rate limiting,  WS p roxy |
| **In-Memory Guard** | Redis | A tomi c Lua stock decrement, idempotency cache,  Pu b/Sub |
| **Primary Database** | PostgreSQ L  | Relational ACID store of record (orders,  c ustomers, events) |
| **Job Queue** | Rabbit  MQ | Resilient worker dispatch with ACK/NACK,   retries, DLQ |
| **Event Log** | Apache Kaf k a | Immutable event stream for audit trails  &  analytics replay |
| **API Layer** | Spri ng  for GraphQL | Flexible queries & WebSocke t re al-time subscriptions |
| **Orchestratio n** |  Docker Compose | Reproducible local cl uster  (`docker compose up`) |
| **Load Testi ng** |  k6 | High-throughput concurrent load  & overse ll verification |
  
---

## 🚀 Ru nning Locally with Docker Compose

Spin up th e entire cluster (PostgreSQL, Redis, RabbitMQ , 3 Spring Boot instances, and Nginx reverse  proxy):

```bash
# 1. Clone repository
git cl one https://github.com/alshwadfy/FlashTix.git 
cd FlashTix

# 2. Start all services
docker  compose up --build -d

# 3. Verify health
doc ker compose ps
```

- **Load Balanced Gateway  (Nginx)**: `http://localhost:80`
- **RabbitM Q Management Dashboard**: `http://localhost:1 5672` (guest / guest)
- **PostgreSQL**: `loca lhost:5432` (user / user, database: `flashtix `)
- **Redis**: `localhost:6379`

### Verific ation & Chaos Testing (k6)

Run the automated  k6 load and chaos test suites:

```bash
# Mi lestone 1: Concurrent flash sale simulation ( 5,000 requests, 100 stock)
k6 run k6/flash_sa le_test.js

# Milestone 2: Chaos engineering  failover test (killing app node mid-flight)
k 6 run k6/chaos_failover_test.js

# Milestone  3: Asynchronous workers, DLQ, and SAGA compen sation test
k6 run k6/rabbitmq_saga_test.js
` ``

### Detailed Milestone Verification Repor ts
- [Milestone 1 Benchmark Report: Redis Lua & Zero Oversell](docs/MILESTONE_1_REPORT.md) 
- [Milestone 2 Chaos Report: Nginx HA & Failover](docs/MILESTONE_2_REPORT.md)
- [Milestone 3 Asynchronous Workers & SAGA Compensation](docs/MILESTONE_3_REPORT.md) 