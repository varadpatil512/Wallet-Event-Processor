# DECISIONS.md — Wallet Event Processor

---

## 1. How did you handle the concurrency race condition?

This project has **two distinct concurrency problems**, solved by two different mechanisms.

---

### a) Idempotent Duplicate Request Handling (same `transactionId`)

**Approach:** Database-level unique constraint on `TransactionRecord.transactionId`.

> An in-application `if (exists) then skip` check was deliberately avoided — under true concurrency, that check-then-act pattern is itself a race condition.

#### How it works

1. Every incoming request first attempts to **insert** a `TransactionRecord` row via `saveAndFlush()`, claiming ownership of that `transactionId`.
2. If the insert **succeeds** → this thread owns the transaction and proceeds to process the debit.
3. If the insert **fails** with `DataIntegrityViolationException` (unique constraint violation) → another thread already claimed this `transactionId`. This thread fetches the existing record and **replays the original response** (`200 OK`), without reprocessing the debit.

#### Why `REQUIRES_NEW`?

The claim step runs in a dedicated `@Transactional(propagation = Propagation.REQUIRES_NEW)` method inside `TransactionClaimService`. This isolation is essential:

- A failed claim (duplicate) rolls back **only its own transaction**, leaving the caller's persistence context clean.
- Without this isolation, a flush exception inside the same transaction corrupts the JPA session — subsequent queries on that session fail silently, turning duplicate replays into `500` errors.

**Test result:** 3 concurrent identical requests → **exactly one real debit**, two clean replays, all returning `200 OK`.

---

### b) Preventing Negative Balances Under Concurrent Debits

**Approach:** Pessimistic row-level locking via `SELECT ... FOR UPDATE`.

`WalletRepo` exposes `findByUserIdForUpdate(userId)` annotated with `@Lock(LockModeType.PESSIMISTIC_WRITE)`, which Hibernate translates to `SELECT ... FOR UPDATE`. Any thread that acquires this lock **blocks all other threads** attempting the same lock on that row until the first transaction commits or rolls back — concurrent debits against the same wallet are serialized into a safe, one-at-a-time sequence.

The full flow — claim → lock wallet → validate balance → debit → update ledger — runs inside a single `@Transactional` method. The lock is held for the full duration and released automatically on commit.

**Test result:** 10 concurrent ₹100 debits against a ₹500 wallet → **exactly 5 succeed** (`200 OK`), **exactly 5 fail** (`422 Unprocessable Entity` — insufficient funds), final balance exactly **₹0.00** — no negative balance, no lost updates.

---

## 2. Where did the AI assistant give an incorrect or sub-optimal suggestion?

Real debugging happened here — documenting these honestly.

---

### Issue 1 — RestTemplate / Dependency Version Mismatch

The project was initially scaffolded against **Spring Boot 3.2.x conventions**, but the actual resolved version was **Spring Boot 4.1.1**. This meant the old `TestRestTemplate` packaging (`spring-boot-starter-test`) no longer applied — Spring Boot 4.x ships `TestRestTemplate` in a separate `spring-boot-resttestclient` module with entirely new import paths:

```java
// Spring Boot 4.x — new module, new imports
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
```

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-resttestclient</artifactId>
    <scope>test</scope>
</dependency>
```

The AI generated code assuming 3.x conventions — the resolution required adapting to the actual resolved environment rather than the assumed one.

---

### Issue 2 — Idempotency Logic Corrupted the Hibernate Session

The AI's first version caught `DataIntegrityViolationException` from a duplicate-insert attempt and then **immediately queried the database again within the same `@Transactional` method**. This is invalid under JPA — once a flush throws a persistence exception, that session is marked corrupted for the remainder of the transaction.

It surfaced as:
```
HHH000099: an assertion failure occurred... session flushed after an exception occurs
```

This silently turned 2 of every 3 duplicate requests into `500` errors instead of clean replays.

**Fix:** Isolate the claim insert into a separate `@Transactional(propagation = REQUIRES_NEW)` service (`TransactionClaimService`), so a failed claim rolls back independently without touching the caller's persistence context.

---

### Issue 3 — `HttpStatus` Enum Identity Mismatch in Test Assertions

Test assertions used reference equality to check for `422`:

```java
// Broken — reference equality on enum constants
r.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY
```

In the resolved Spring version, the actual returned status object was `UNPROCESSABLE_CONTENT` — same HTTP code (422), but a **differently named enum constant**. The `==` comparison silently returned `false` even though the business logic was correct, producing a misleading test failure that looked like a locking bug.

**Fix:** Compare by numeric value instead of enum identity:

```java
// Correct — compare by HTTP status code value
r.getStatusCode().value() == 422
```

---

*Authored during development — September 2026*
