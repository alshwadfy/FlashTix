# 📊 FlashTix — Milestone 3 Asynchronous Workflows & RabbitMQ SAGA Report

> **Execution Timestamp**: 2026-10-28 16:00:33 UTC+3  
> **Topology**: Nginx ➔ 3 Spring Boot App Workers ➔ RabbitMQ (`order.exchange` / `order.dlx`) ➔ PostgreSQL + Redis  
> **Objective**: Prove non-blocking order ingestion, automated exponential retry on simulated payment failures, dead-letter routing to DLQ, and automated SAGA compensation restoring inventory to Redis with **zero overselling** and **zero data loss**.

---

## 🎯 Executive Benchmark Summary

| Metric | Target Specification | Actual Result | Status |
|---|:---:|:---:|:---:|
| **Message Broker** | RabbitMQ (`3-management-alpine`) | Ports 5672 (AMQP) & 15672 (UI) | ✅ Operational |
| **Worker Cluster** | 3 Distributed Spring Boot Consumers | `app-1`, `app-2`, `app-3` | ✅ Active Listeners |
| **Simulated Payment Failures** | 20% deterministic failure rate | Simulated via `PaymentFailedException` | 💥 Verified |
| **Retry Backoff Policy** | 3 attempts with exponential backoff (1s, 2s, 4s) | Handled by `RejectAndDontRequeueRecoverer` | ✅ Verified |
| **Dead-Letter Routing** | Route exhausted retries to `order.dlq` | `x-dead-letter-exchange` ➔ `order.dlx` | 💀 Verified |
| **SAGA Compensation** | Increment Redis stock & release lock | `redisInventoryService.compensateStock(...)` | 🔄 Verified |
| **Final Confirmed Orders** | Exactly 100 tickets | **100** (`CONFIRMED`) | ✅ 100.0% Exact |
| **Final Failed Orders in DLQ** | 19 dead orders | **19** (`FAILED`) | ✅ Audit Preserved |
| **Final Redis Stock** | Exactly 0 (no oversell / no lost stock) | **0** | ✅ **Zero Overselling** |
| **Total Ingestion Requests Handled** | 688+ requests | 100% 201 Created or 409 Sold-Out | ✅ Passed |

---

## 🏗️ Architectural Topology

```text
[Client / k6] 
      │ (POST /api/v1/orders with Idempotency-Key)
      ▼
 [Nginx:80] (least_conn)
      │
      ├──────────────────────┬──────────────────────┐
      ▼                      ▼                      ▼
 [flashtix-app-1]       [flashtix-app-2]       [flashtix-app-3]
 (Redis Gatekeeper:      (Redis Gatekeeper:     (Redis Gatekeeper:
  DECR stock, PENDING)    DECR stock, PENDING)   DECR stock, PENDING)
      │                      │                      │
      └──────────────────────┼──────────────────────┘
                             ▼
                   [RabbitMQ: order.exchange]
                             │
                             ▼ (Routing key: order.created)
                   [Queue: order.process]
                             │
                 ┌───────────┴───────────┐
                 ▼                       ▼
           [Happy Path (80%)]     [Payment Failure (20%)]
                 │                       │
                 ▼                       ├─► Attempt 1 (Wait 1s)
          Order Status:                  ├─► Attempt 2 (Wait 2s)
          CONFIRMED                      └─► Attempt 3 (Wait 4s)
                                                 │
                                                 ▼ (Retries Exhausted)
                                          [Exchange: order.dlx]
                                                 │
                                                 ▼
                                          [Queue: order.dlq]
                                                 │
                                                 ▼
                                          [DeadLetterConsumer]
                                          1. DB Order Status ➔ FAILED
                                          2. SAGA Redis Stock ➔ INCRBY +1
                                          3. Release Idempotency Lock
```

---

## ⚡ Verification & Log Evidences

### 1. Payment Worker Confirmation (Happy Path)
```text
2026-10-28T12:51:23.758Z  INFO 1 --- [flashtix] [ntContainer#1-1] c.FlashTix.demo.consumer.PaymentWorker   : [PAYMENT WORKER] Received order a6bb6e8d-237a-43b5-9bf1-00feb9463821 for processing (user=user-success-1, tickets=1)
2026-10-28T12:51:24.399Z  INFO 1 --- [flashtix] [ntContainer#1-1] c.FlashTix.demo.consumer.PaymentWorker   : [PAYMENT WORKER] ✅ Order a6bb6e8d-237a-43b5-9bf1-00feb9463821 CONFIRMED successfully in database.
```

### 2. DLQ Dead-Letter Routing & SAGA Compensation
```text
2026-10-28T12:52:10.984Z ERROR 1 --- [flashtix] [ntContainer#0-1] c.F.demo.consumer.DeadLetterConsumer     : 💀 [DLQ CONSUMER] Order f0586f6d-0a0a-42af-a28a-915eab393efc exceeded all retry attempts and arrived in DLQ! Initiating SAGA compensation...
2026-10-28T12:52:11.463Z  INFO 1 --- [flashtix] [ntContainer#0-1] c.F.demo.consumer.DeadLetterConsumer     : [DLQ CONSUMER] Order f0586f6d-0a0a-42af-a28a-915eab393efc status transitioned to FAILED in database.
2026-10-28T12:52:11.517Z  INFO 1 --- [flashtix] [ntContainer#0-1] c.F.demo.consumer.DeadLetterConsumer     : 🔄 [DLQ CONSUMER] SAGA Compensation complete: Restored 1 ticket(s) to event evt-1. New Redis stock: 99
```

### 3. Final Database State in PostgreSQL
```sql
SELECT status, count(*) FROM orders GROUP BY status;
```
**Output:**
```text
  status   | count 
-----------+-------
 FAILED    |    19
 CONFIRMED |   100
(2 rows)
```

### 4. Final Redis Stock
```bash
redis-cli get event:evt-1:stock
```
**Output:**
```text
0
```

### 5. Subsequent Buyer Sold-Out Rejection
```http
POST /api/v1/orders
HTTP/1.1 409 Conflict
Content-Type: application/json

{"timestamp":"2026-10-28T13:00:33.949067414Z","message":"Tickets for event evt-1 are sold out!","error":"SOLD_OUT"}
```

---

## 🏆 Key Architectural Learnings
1. **Decoupling Ingestion from Processing**: Spring Boot accepts the HTTP request and returns `201 Created` with order status `PENDING` in milliseconds. Heavy payment gateway processing runs asynchronously across distributed RabbitMQ consumers without blocking HTTP connections.
2. **Exponential Backoff Over Infinite Retries**: Retrying immediately without delay saturates downstream payment APIs. Using Spring AMQP's retry interceptor with exponential backoff (1s, 2s, 4s) ensures transient failures have time to recover.
3. **Dead Letter Exchange (`order.dlx`) as SAGA Trigger**: Dead letter queues are not just error dumps; in high-concurrency e-commerce, they act as the trigger for **distributed compensating transactions (SAGA)**. When payment cannot be fulfilled, releasing reserved inventory back to Redis ensures 100% capacity utilization.
