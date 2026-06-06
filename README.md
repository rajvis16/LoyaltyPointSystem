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
git clone [https://github.com/rajvis16/LoyaltyPointSystem](https://github.com/rajvis16/LoyaltyPointSystem)
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