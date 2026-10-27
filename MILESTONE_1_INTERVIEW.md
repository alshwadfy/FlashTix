# 🎯 FlashTix: Milestone 1 Senior Engineer Interview (7 Core Questions)

> **Scenario**: We are launching a flash sale with **5,000 concurrent users** competing for **100 tickets**. 
> As a Senior Engineer interviewing you on this codebase, I want to see if you truly understand the concurrency mechanics, distributed edge cases, and failure modes of the system we just built.

---

### 1. The Multi-Node Bottleneck & Database Crash
Why can’t we simply use a Java `synchronized` block or `ReentrantLock` in Spring Boot, or a PostgreSQL `SELECT ... FOR UPDATE` row-level lock? What happens to the database and thread pool if 5,000 concurrent requests hit PostgreSQL directly?


- using synchronized wont will drop some data off cuz there is nothing to store data in causing erros to the users , even if we skipped this important part a 5000 request at a time will destroy our database will make many sellings for the same seat cuz of the race condition 
---

### 2. The Network Round-Trip Trap (Why Lua?)
If Redis is already in-memory and super fast, why can't we just call:
```java
int stock = redisTemplate.opsForValue().get("event:evt-1:stock");
if (stock >= 1) {
    redisTemplate.opsForValue().decrement("event:evt-1:stock");
}
```
What exact race condition happens between lines 1 and 3 over the network, and how does running a **Lua script** inside Redis eliminate this race condition completely?

- so we initialize the lua script before any requests start coming that makes the flow to be steady for requests in the lines 1 and 3 it gets the tickets from the DB and then decreases it what if there were 2 requests at a time lua scripts running in it is in the queue waiting for the lua script to finish the current decreament to pass the other requests


---

### 3. Redis Single-Threaded Engine & Blast Radius
Redis executes Lua scripts atomically on its single-threaded event loop. 
- What does this mean for the other 4,999 requests while Redis is executing a Lua script for request #1?
- What is the operational danger if an engineer writes slow, complex, or looping code inside a Redis Lua script during a flash sale?

- single thread means one process at a time so every 4999 waits till the first one finishes
- it will make the requests wait for so much that might get lost 

---

### 4. In-Flight vs. Completed Idempotency (The 2ms vs 10s Double-Click)
Walk me through what happens under two distinct scenarios:
- **Scenario A**: A user double-clicks "Buy" within **2 milliseconds** (both requests in flight at the exact same time).
- **Scenario B**: A user clicks "Buy", gets their ticket, but their browser auto-retries **10 seconds later** with the same `Idempotency-Key`.
How do our Redis `setIfAbsent` lock and PostgreSQL unique constraint coordinate to protect the user in both cases?


- A : the request reaches to the shields in create order that sets dublicates to true the second order cannot pass if it passed somehow , it reaches for the idempotency key lock that blocks any repeated request with the same key

- B : it sets the key to processing if it was first time we see it its ok it not we return false
---
### 5. The TTL Dilemma on Idempotency Locks
In [RedisInventoryService.java](file:///d:/std/SpringBoot/Project%204/demo/src/main/java/com/FlashTix/demo/service/RedisInventoryService.java), we set an expiration TTL of **5 minutes** on the idempotency key:
- What disaster happens if an engineer sets **no TTL** (infinite) and the app server crashes mid-request?
- What disaster happens if an engineer sets the TTL to **1 second**?


- so the TTL is time to live of the mapped row in redis we are mapping the idempotency key if it was infinite we will have so many data that we dont need to save in our cache taking so much memory for no important data 

- if it was 1 sec user can send many requests after 1 sec that the app might guess its a new request
---

### 6. The Dual-Write Problem & SAGA Compensation
Look at the sequence in [OrderService.java](file:///d:/std/SpringBoot/Project%204/demo/src/main/java/com/FlashTix/demo/service/OrderService.java):
1. Redis stock is decremented from `100 ➔ 99`.
2. PostgreSQL tries to save the order as `PENDING`.
3. PostgreSQL connection drops or throws a timeout exception!

If our `catch` block did **nothing**, what permanent bug is introduced into our ticketing inventory? How does our compensation code resolve this?

- we have a lost ticket after the DB crashes the redis never compensates the user paid and its still valid in the app 

-if any error occurd it resets as nothing happens return the ticket where it came back from 
---

### 7. Disk I/O & Spring Boot Lifecycle (`@PostConstruct`)
Why did we load the Lua script in `@PostConstruct` using `DefaultRedisScript<Long>` instead of reading `decrement_stock.lua` from the classpath inside `tryAcquireStock()` whenever a request arrives? What happens to CPU and disk throughput under 5,000 requests if you don't do this?

- to make the redis ready with the script to not make any crashes in the DB cuz it takes time to get initiaized 5000 requests at a time in redis before fetching the sccript will absloutly destroy our logic, fetching the script every time takes so much it should be ready in memory for usage instead of fetching it onevery request 
---

*(Whenever you're ready, answer Question 1 or tackle whichever question you want to explain first!)*
