# FinLedger System - High-Level Overview

## What We Built

A **production-grade double-entry accounting system** in Java/Spring Boot that handles financial transactions with strong consistency guarantees.

---

## Architecture

```
Controller (REST API)
    ↓
Service (@Transactional atomic operations)\
    ↓
Repository (Spring Data JPA)
    ↓
Database (PostgreSQL with Liquibase migrations)
```

**Tech Stack:** Java 17, Spring Boot 4.0, PostgreSQL, Kafka-ready, Redis-ready

---

## Core Features

### **1. Double-Entry Accounting (Feature 2)**
Every transaction creates exactly **2 journal entries**:
- **DEBIT** on source account (money out)
- **CREDIT** on destination account (money in)

**Why it matters:** Prevents money creation/deletion bugs. Balance always = sum of credits - debits.

```java
// One transaction = two journal entries (atomic)
postTransfer() {
    create DEBIT entry on source
    create CREDIT entry on destination
    // Both or neither — no partial state
}
```

---

### **2. @Transactional Atomicity**
All 11 steps in `postTransfer()` wrapped in **one PostgreSQL transaction**:
```
1. Check duplicate (idempotency)
2. Validate source account exists
3. Validate destination account exists
4. Validate accounts active
5. Validate currency match
6. Validate amount > 0
7. Check sufficient balance
8. Create Transaction (PENDING)
9. Create DEBIT entry
10. Create CREDIT entry
11. Update Transaction to SETTLED
```

**If ANY step fails → entire transaction rolls back.** No partial updates.

---

### **3. Optimistic Locking with @Version**
Handles concurrent writes safely:

```java
@Entity
class Account {
    @Version
    Long version;  // JPA increments on each update
}
```

**How it works:**
- Thread 1 reads Account (version=1)
- Thread 2 reads Account (version=1)
- Thread 1 updates → version becomes 2
- Thread 2 tries to update → OptimisticLockException (version mismatch)
- Service retries Thread 2 (up to 3x)

**Result:** No lost updates, no deadlocks.

---

### **4. Idempotency Protection**
Same request = same result, even if replayed:

```java
// First call
POST /api/transactions
{ "idempotencyKey": "txn-123", "amount": 100 }
→ Creates transaction, returns response

// Duplicate call (same idempotency key)
POST /api/transactions
{ "idempotencyKey": "txn-123", "amount": 100 }
→ Throws DuplicateTransactionException
→ Client knows transaction already processed
```

**Why it matters:** Network failures, retries, webhooks won't create duplicate charges.

---

### **5. Balance Validation (No Negative Balances)**
Before posting DEBIT:
```java
sourceBalance = SELECT SUM(credits) - SUM(debits) FROM journal_entries
if (sourceBalance < transactionAmount) {
    throw InsufficientBalanceException
}
```

**Prevents:** Overdrafts, money creation from nowhere.

---

## Key Design Decisions

| Decision | Why |
|----------|-----|
| **Double-entry** | Accounting standard. Every transaction has source & destination. Prevents balance corruption. |
| **@Transactional** | All-or-nothing semantics. Partial states impossible. |
| **@Version** | Concurrent write safety without locks. Production-grade. |
| **Idempotency key** | Handles network retries, duplicate requests. Essential for payments. |
| **Liquibase** | Schema version-controlled. Same DDL on dev/test/prod. |
| **Unique index on idempotency_key** | Database enforces uniqueness. No race conditions. |
| **Indexes on status, created_at** | Query performance. O(1) lookups instead of O(n). |

---

## What We Avoided

❌ **Manual balance calculation** → Wrong under concurrency  
❌ **Hardcoded currency conversions** → Won't scale  
❌ **Soft deletes** → Business audit trail handled via status fields  
❌ **Denormalized balances** → Always calculated from journal entries (source of truth)  

---

## What's Production-Ready

✅ **Full ACID compliance** (PostgreSQL + @Transactional)  
✅ **Concurrent write safety** (optimistic locking)  
✅ **Duplicate prevention** (idempotency + DB unique constraint)  
✅ **Error handling** (7 custom exceptions + global handler)  
✅ **Database migrations** (Liquibase auto-applied)  
✅ **Comprehensive tests** (13 unit tests covering happy path + 9 error paths)  

---

## Interview Talking Points

**"How do you ensure consistency in a payment system?"**
> We use @Transactional to make postTransfer atomic — all 11 steps succeed or all fail. Double-entry accounting ensures money can't be created/deleted. @Version handles concurrent writes with optimistic locking + retries.

**"What if two requests process the same transaction simultaneously?"**
> Idempotency key + unique DB constraint ensures first write wins. Second request gets DuplicateTransactionException. Network retries are safe.

**"How do you prevent overdrafts?"**
> Before posting DEBIT, we calculate current balance from journal_entries (source of truth) and validate it's >= transaction amount. This prevents negative balances even under concurrent writes.

**"Why double-entry accounting?"**
> It's the accounting standard. Every transaction must debit one account and credit another. If balance goes wrong, you can audit which journal entry is wrong. Prevents bugs where money is created/deleted.

**"How do you handle database migrations in production?"**
> Liquibase manages all schema changes. Every migration is version-controlled. Same DDL runs on dev/test/prod with Liquibase tracking which migrations were applied. Rollbacks are built-in.

---

## Metrics

- **13 Unit Tests** covering happy path + error scenarios
- **7 Custom Exceptions** (DuplicateTransaction, InsufficientBalance, etc.)
- **3 Database Indexes** on frequently-queried columns (status, idempotency_key, created_at)
- **1 Atomic Method** with 11 sequential steps, zero partial states
- **3 Database Tables** (accounts, transactions, journal_entries)
- **2 Journal Entries** per transaction (DEBIT + CREDIT)

---

## Next Phases

**Feature 3:** Reporting (trial balance, account statements)  
**Feature 4:** Settlement (async Kafka for reconciliation)  
**Feature 5:** Idempotency Keys (separate feature for other endpoints)  

---

## Code Quality

- Zero manual SQL (Spring Data JPA queries)
- Zero hardcoded values (@Column constraints, @Version for concurrency)
- Zero business logic in controllers (service layer owns it)
- Zero duplicate code (DTOs separate from entities)
- Production error handling (structured ErrorResponse, proper HTTP status codes)
