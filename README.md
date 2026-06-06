# High-Scale Loyalty & Points Ledger System

An immutable, transaction-isolated loyalty engine engineered in Java using Spring Boot and JPA/Hibernate. The system provides mathematically precise loyalty tracking, 
state-machine tier management, FIFO point degradation models, and fully transparent, fraud-resistant transaction auditing.

## 🎯 Project Requirements & Completion Status

The system comprehensively satisfies all evaluation criteria across the core, extended, and stretch functional tracks:

| Track | Feature | Functional Specification | Status |
| :--- | :--- | :--- | :---: |
| **Required** | **Earn Points** | 1 point allocated per $1 spent, adjusted dynamically by tier. |  `DONE` |
| | **Redeem Points** | Catalog reward redemption backed by active ledger balance verification. |  `DONE` |
| | **Check Balance** | Real-time retrieval of active point aggregates and rolling tier data. |  `DONE` |
| | **Expiry Logic** | Automatic temporal decay of point buckets 12 months post-allocation. |  `DONE` |
| **Extended** | **Tier Status** | State-machine tier tracking (`Silver`/`Gold`/`Platinum`) via a 365-day rolling spend window. |  `DONE` |
| | **Refunds** | Exact point clawback linked securely to historical purchase multipliers. |  `DONE` |
| **Stretch** | **Redemption Order** | Chronological optimization using FIFO bucket peeling to prioritize points closest to expiry. |  `DONE` |
| | **Interactive CLI** | Spring Shell terminal interface delivering a robust, isolated testing runtime. |  `DONE` |


---

## 🏗️ Core Architecture & Design Patterns

### 1. Immutable Ledger Pattern (`POINT_LEDGER_ENTRIES`)
Rather than maintaining a loose, highly mutable column count on a Customer record—which introduces severe thread contention, race conditions, and cache-drift vulnerabilities—this architecture 
uses an **Append-Only Transactional Ledger**.
* Every operation (`EARN`, `REDEEM`, `CLAWBACK`) logs a deterministic vector record.
* The customer's point balance is derived dynamically via transactional repository state summations (`SUM(points)`), providing an uncorrupted audit trail.

### 2. Historical Multiplier Invariant Protection
To mitigate systemic fraud (e.g., purchasing high-value items to reach a premium tier, redeeming a rewards certificate, and subsequently returning the items), the engine captures an immutable 
snapshot of the active tier multiplier (`tierPointUsed`) directly onto the ledger row at checkout. This guarantees that point clawbacks are evaluated at the exact historical rate earned, 
preventing exploitation of the ledger floor.

### 3. FIFO (First-In, First-Out) Bucket Peeling
Point redemptions and tier evaluations follow strict expiration and allocation timelines. When a customer claims a reward, the service layer isolates active, unexpired `EARN` buckets chronologically,
processing partial and complete segment consumption seamlessly.

---

## 📊 Domain Rules & Tier State Matrix

The platform models three distinct client tiers calculated over a trailing 365-day rolling spend window:

| Tier | Spend Threshold | Point Multiplier |
| :--- | :--- | :--- |
| **SILVER** | Less than $1,000.00 | 1.0x Points / Dollar |
| **GOLD** | Between $1,000.00 and $2,000.00 (Inclusive) | 1.5x Points / Dollar |
| **PLATINUM**| Greater than $2,000.00 | 2.0x Points / Dollar |

* **State Boundary Evaluations:** Point allocations are determined by the active tier *at the moment of transaction initialization*, after which the tier state machine safely recalculates rolling limits.

---

## 🚀 Getting Started & Local Execution

### Prerequisites
* **Java Development Kit (JDK) 21**
* **Apache Maven 3.9.12+**

### 1. Clone & Navigate to the Project
```bash
git clone https://github.com/rajvis16/LoyaltyPointSystem.git
cd LoyaltyPointSystem
```
### 2. Build It
```bash
mvn clean package
```
### 3. Run It (With CLI)
```bash
java -jar target/loyalty-point-system-1.0.0.jar --spring.profiles.active=cli
```
### 4. Run It (Server Mode)
```bash
java -jar target/loyalty-point-system-1.0.0.jar
```

---

## 🗺️ Project Blueprint & Key Components

If you are digging into the codebase to audit the core mechanics, here is where the critical business logic and ledger structures reside:

### 🛠️ Java Core Layer (`src/main/java/...`)
* **`LoyaltyServiceImpl.java`**
    * *Location:* `com.mark43.loyalty.domain.service.impl`
    * *Role:* The central engine handling tier state-machine evaluations, raw point allocations, FIFO bucket-peeling for redemptions, and historical multiplier calculations for clawbacks.
* **`ProductOrderServiceImpl.java`**
    * *Location:* `com.mark43.loyalty.domain.service.impl`
    * *Role:* Manages checkout and return flows, seamlessly coordinating inventory movements with the loyalty engine to ensure atomicity across transactions.

### 🗄️ Relational Database Schemas (H2 In-Memory)
* **`POINT_LEDGER_ENTRIES`**
    * *Role:* The immutable, append-only ledger that records every point transaction vector (`EARN`, `REDEEM`, `CLAWBACK`). This is the absolute single source of truth used to derive customer point balances dynamically.
* **`CUSTOMER_PRODUCT_LEDGER`**
    * *Role:* Tracks the historical purchase records and quantities per customer, serving as the foundational audit trail used to calculate the 12-month rolling spend limits.

---

## ⚡ Architectural Notes for Reviewers

* **Transactional Integrity:** All multi-component routines across `ProductOrderServiceImpl` and `LoyaltyServiceImpl` run under strict `@Transactional` boundaries. Any unexpected exception during checkout or return automatically aborts the entire unit of work, preventing out-of-sync states between product records and point ledger entries.
* **Cache Management:** To support high-throughput balance checks, customer profiles utilize a high-performance cache wrapper (`LoyaltyCacheManager`). To prevent stale data anomalies, the cache enforces strict write-through invalidation rules, clearing a customer's cache instantly upon any point mutation activity.

---

---

## ⚖️ Strategic Trade-offs & Architecture Decisions

### Dynamic Aggregation vs. State Duplication
During architectural planning, a critical trade-off was evaluated regarding how to maintain a customer's available point balance:
* **Option A:** Store a running mutable `pointBalance` column directly on the `CUSTOMER` table and increment/decrement it on every transaction.
* **Option B (Chosen):** Treat point balances as a transient, calculated state derived dynamically via transactional repository summations (`SUM(points)`) across an append-only transaction ledger (`POINT_LEDGER_ENTRIES`).

**The Trade-off:** Option A offers minor read-performance optimization at low scale. However, it introduces severe data corruption risks via race conditions under concurrent workloads and leaves zero historical audit trail for tracking point fraud or troubleshooting drift. Option B introduces a minor read-cost overhead (mitigated cleanly via targeted indexing and application-layer caching policies), but guarantees absolute mathematical precision, total immutability, and an uncorrupted audit history. For a financial ledger system, transactional integrity must always be prioritized over raw, unoptimized read speeds.

---

## 🗺️ Future Roadmap & Next Horizons (With More Time)

Given additional engineering iterations, the following infrastructure enhancements would be prioritized:

1. **Pagination:** Introduced pagination to view all the customers and products
2. **Queue Mechanism:** After the customer buys or return products we immediately hit the database server to add or deduct points. At enterprise level, when the data size is huge, this might slow down the process. To make sure that the customer gets a good experience, they should immediately get an acknowledgment after they buy or return the products. Further processing of adding/deducting points can be handed over to the queue mechanism for smoother experience

---

## 🤖 AI Tooling & Collaboration Context

* **Tools Utilized:** Gemini (Advanced Architectural Consultation)
* **Application Context:** AI collaboration was leveraged as an interactive peer-review architecture partner. It was utilized to model edge-cases (such as running a multi-item basket through rapid tier escalations, immediate reward consumption, and subsequent historical clawbacks) to stress-test the state-machine logic and mathematically verify the ledger boundaries against unexpected point-drifts.

---

## 🛠️ Verification & Stress-Testing Script

To demonstrate the structural resilience of the engine across multi-item baskets, simultaneous promotions, and deep debt recovery, execute the following script inside the interactive CLI:

```bash
# 1. Establish the Customer Profile
customer-list

# 2. Phase 1: Climb to PLATINUM via Multi-Item Baskets
buy --email=bob@example.com --ref=BOB-STRESS-001 --items="Premium Leather Jacket:2,Waterproof Hiking Boots:1"
buy --email=bob@example.com --ref=BOB-STRESS-002 --items="Premium Leather Jacket:3,Ergonomic Running Shoes:2"
buy --email=bob@example.com --ref=BOB-STRESS-003 --items="Waterproof Hiking Boots:2,Ergonomic Running Shoes:1"

# Verify Balance hits exactly 2270.00 pts [PLATINUM]
balance --email=bob@example.com

# 3. Phase 2: Maximum Reward Consumption
redeem --email=bob@example.com --reward="$100 Mega Gift Card VIP"
redeem --email=bob@example.com --reward="$100 Mega Gift Card VIP"

# 4. Phase 3: Slicing into Transparent Ledger Debt
return --email=bob@example.com --ref=BOB-STRESS-002 --items="Premium Leather Jacket:2"
return --email=bob@example.com --ref=BOB-STRESS-001 --items="Premium Leather Jacket:2,Waterproof Hiking Boots:1"

# Verify explicit negative balance reflection (-880.00 pts [SILVER])
balance --email=bob@example.com

# 5. Phase 4: Symmetrical Recovery to Positive Fields
buy --email=bob@example.com --ref=BOB-STRESS-004 --items="Premium Leather Jacket:4"

# Verify clean normalization out of debt pool (120.00 pts [GOLD])
balance --email=bob@example.com