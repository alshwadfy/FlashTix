# 📊 FlashTix — Milestone 1 Verification & Benchmark Report

> **Test Executed At**: 2026-10-28 00:15:31 UTC+3  
> **Target Endpoint**: `POST /api/v1/orders`  
> **Objective**: Sell a strictly limited stock of 100 tickets to 5,000 concurrent requests with **zero overselling**, **zero 500-errors**, and **100% idempotency**.

---

## 🎯 Executive Summary

| Metric | Target | Actual Result | Status |
|---|:---:|:---:|:---:|
| **Initial Available Stock** | 100 | **100** | ✅ Verified |
| **Total Ingestion Requests** | 5,000 | **5,000** | ✅ 100% Completed |
| **Successful Orders Created (HTTP 201)** | Exactly 100 | **100** | ✅ Passed (100.0%) |
| **Rejected Sold-Out Requests (HTTP 409)** | Exactly 4,900 | **4,900** | ✅ Passed (100.0%) |
| **Unexpected Errors (5xx, 4xx drops)** | 0 | **0** | ✅ Passed (0.00%) |
| **Final Redis Stock** | 0 | **0** | ✅ Verified |
| **Final PostgreSQL `orders` Count** | 100 | **100** | ✅ Verified |
| **Oversold Tickets** | 0 | **0** | ✅ **Zero Overselling** |

---

## ⚡ k6 Load Test Detailed Breakdown

### Test Configuration
- **Tool**: Grafana k6 (`grafana/k6:latest`) running via Docker container
- **Execution Mode**: Local shared iterations
- **Virtual Users (VUs)**: 100 concurrent workers
- **Total Iterations**: 5,000 requests
- **Test Duration**: 10.8 seconds
- **Effective Throughput**: **463.99 requests / second**

```
          /\      Grafana   /‾‾/  
     /\  /  \     |\  __   /  /   
    /  \/    \    | |/ /  /   ‾‾\ 
   /          \   |   (  |  (‾)  |
  / __________ \  |_|\_\  \_____/ 

  █ THRESHOLDS 
    ✓ orders_created ................: count == 100  (Actual: 100)
    ✓ orders_sold_out ...............: count == 4900 (Actual: 4900)
    ✓ unexpected_errors .............: count == 0    (Actual: 0)

  █ CHECKS
    ✓ status is 201 or 409 ..........: 100.00% (5000 out of 5000)
```

---

## ⏱️ HTTP Request Latency Statistics

| Percentile / Metric | Response Time | Description |
|---|:---:|---|
| **Minimum (`min`)** | **7.14 ms** | Fastest rejected request (early Redis Lua return) |
| **Median (`med`)** | **134.28 ms** | 50% of all requests resolved within 134ms |
| **Average (`avg`)** | **205.05 ms** | Mean duration across all 5,000 requests |
| **p90** | **304.32 ms** | 90% of requests resolved under 305ms |
| **p95** | **434.14 ms** | 95% of requests resolved under 435ms |
| **Maximum (`max`)** | **3.02 s** | Peak time during the initial cold concurrency surge |

---

## 🗄️ Database & Cache Verification Commands

### 1. PostgreSQL Order Table Count
```sql
SELECT status, COUNT(*) FROM orders GROUP BY status;
```
**Output:**
```text
 status  | count 
---------+-------
 PENDING |   100
(1 row)
```

### 2. Redis Inventory Key Check
```bash
GET event:evt-1:stock
```
**Output:**
```text
0
```

### 3. REST API Stock Verification
```http
GET /api/v1/events/evt-1/stock
```
**Response:**
```json
{
  "eventId": "evt-1",
  "availableStock": 0
}
```

---

## 🛡️ Idempotency Verification Test

A standalone idempotency test was executed by firing duplicate requests with identical `Idempotency-Key` headers:

1. **First Request**:
   - Status: `HTTP 201 Created`
   - Body: `{"orderId": "32f8df41-...", "status": "PENDING", "duplicate": false}`
   - Redis stock: decremented `100 -> 99`
2. **Second Duplicate Request**:
   - Status: `HTTP 200 OK` (Stripe idempotency pattern)
   - Body: `{"orderId": "32f8df41-...", "status": "PENDING", "duplicate": true}`
   - Redis stock: **unchanged at 99** (No double decrement, no double billing)

---

## 🏆 Conclusion
Milestone 1 satisfies all acceptance criteria:
- **Zero overselling** proven at 5,000 concurrent requests.
- **In-memory Redis Lua gatekeeper** shielded PostgreSQL from 4,900 unnecessary write operations.
- **PostgreSQL** safely recorded exactly the 100 won orders.
- **Idempotency** guarantees single execution under network retries.
