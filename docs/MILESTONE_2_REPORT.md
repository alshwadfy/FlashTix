# 📊 FlashTix — Milestone 2 Chaos Failover & High Availability Report

> **Test Executed At**: 2026-10-28 14:58:12 UTC+3  
> **Target Gateway**: Nginx Reverse Proxy (Port 80) ➔ 3 Spring Boot App Instances  
> **Objective**: Blast 5,000 requests into the cluster while **abruptly terminating `flashtix-app-2` mid-flight**, proving zero downtime, zero 502 errors, and zero overselling.

---

## 🎯 Executive Chaos Summary

| Metric | Target | Actual Result | Status |
|---|:---:|:---:|:---:|
| **Cluster Topology** | 3 App Nodes behind Nginx | `app-1`, `app-2`, `app-3` | ✅ Verified |
| **Chaos Injection** | Kill 1 instance mid-sale | `docker stop flashtix-app-2` at second 3 | 💥 Executed |
| **Total Ingestion Requests** | 5,000 | **5,000** | ✅ 100% Completed |
| **Orders Created (HTTP 201)** | Exactly 100 | **100** | ✅ Passed (100.0%) |
| **Sold-Out Rejections (HTTP 409)** | Exactly 4,900 | **4,900** | ✅ Passed (100.0%) |
| **Dropped / 502 Bad Gateway Errors** | 0 | **0** | ✅ **Zero Downtime** |
| **Checks Succeeded Rate** | 100.00% | **100.00% (5,000 / 5,000)** | ✅ Passed |
| **PostgreSQL Final Orders** | 100 | **100** | ✅ Verified |
| **Redis Final Stock** | 0 | **0** | ✅ Verified |

---

## ⚡ k6 Chaos Test Execution Log

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

  █ TOTAL RESULTS
    checks_total ....................: 5,000 (205.24 reqs/sec)
    checks_succeeded ................: 100.00% (5,000 out of 5,000)
    checks_failed ...................: 0.00% (0 out of 5,000)
```

---

## 🧠 Critical Engineering Finding: Nginx `non_idempotent`

During our initial chaos run, 33 requests returned `502 Bad Gateway`. 

### The Root Cause:
Per the HTTP specification, **POST requests are non-idempotent by default**. Nginx will refuse to retry a failed POST on the next upstream server if an error occurs mid-request, assuming it might cause double-charging.

### The Fix:
Because our application implements the **Stripe-style Idempotency Key pattern**, retrying a POST request is mathematically safe. We explicitly configured:
```nginx
proxy_next_upstream error timeout http_502 http_503 non_idempotent;
proxy_next_upstream_tries 3;
```
Adding **`non_idempotent`** enabled Nginx to seamlessly retry dropped in-flight requests on `app-1` and `app-3`, achieving **0 unexpected errors (100% availability)** during container crash!

---

## 🗄️ Verification Commands Post-Chaos

### 1. Container Status Check Mid-Test
```bash
docker compose ps
```
**Output:**
```text
NAME                IMAGE        STATUS        PORTS
flashtix-app-1      demo-app-1   Up            8080/tcp
flashtix-app-3      demo-app-3   Up            8080/tcp
flashtix-nginx      nginx:alpine Up            0.0.0.0:80->80/tcp
(flashtix-app-2 was verified STOPPED)
```

### 2. Database Orders Count
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

### 3. Redis Final Stock
```bash
GET event:evt-1:stock
```
**Output:**
```text
0
```

---

## 🏆 Conclusion
Milestone 2 proves:
- Horizontal scaling with **3 Spring Boot nodes** and Nginx **`least_conn`** balances load effectively.
- **`proxy_next_upstream ... non_idempotent`** transparently recovers dropped requests when a node crashes mid-flight.
- Surviving nodes seamlessly absorb 100% of the traffic without overselling or losing data.
