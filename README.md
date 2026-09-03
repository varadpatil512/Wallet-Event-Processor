# Wallet Event Processor

An idempotent payment/wallet event processor built for a real-world backend assignment: ingest webhook-style debit transactions safely under network-retry duplication and concurrent load, without ever double-spending or allowing a negative balance.

## Stack

- Java 17+
- Spring Boot 4.1.x
- Spring Data JPA / Hibernate
- H2 (in-memory database — zero external setup required)
- JUnit 5

## Running the project

No external database, no config, no Postman required.

### Run the application
```bash
mvn spring-boot:run
```
The API will be available at `http://localhost:8080`.

### Run the tests
Open the project in IntelliJ and run `TransactionServiceIntegrationTest` directly — right-click the class → **Run**. All tests spin up a fresh embedded H2 instance and an embedded Tomcat server automatically; nothing needs to be started manually first.

Alternatively, from the command line:
```bash
mvn test
```

## API

### `POST /api/v1/transactions/process`

**Request body:**
```json
{
  "transactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa7",
  "amount": 250.00,
  "type": "DEBIT"
}
```

**Responses:**

| Scenario | Status | Notes |
|---|---|---|
| New transaction, processed successfully | `200 OK` | Wallet debited, ledger updated |
| Duplicate `transactionId` | `200 OK` | Cached original response replayed — balance untouched |
| Insufficient balance | `422 Unprocessable Entity` | No debit applied |
| Wallet not found | `404 Not Found` | |
| Invalid/missing request fields | `400 Bad Request` | Field-level validation errors |

## Core design decisions

Full reasoning, including where AI-assisted development went wrong and had to be corrected, is documented in [`DECISIONS.md`](./DECISIONS.md). Short version:

- **Idempotency** is enforced via a database unique constraint on `transactionId`, with the claim-attempt isolated in its own transaction (`Propagation.REQUIRES_NEW`) so a duplicate never corrupts the caller's persistence context.
- **Negative-balance prevention** is enforced via pessimistic row-level locking (`SELECT ... FOR UPDATE`) on the wallet row, serializing concurrent debits against the same wallet.

## Project structure

```
src/main/java/.../
├── controller/     # REST endpoint
├── service/        # business logic: idempotency claim + locking + debit
├── repository/     # Spring Data JPA repositories
├── entity/         # Wallet, TransactionRecord
├── enums/          # TransactionType, TransactionStat# Wallet Event Processor

An idempotent payment/wallet event processor built for a real-world backend assignment: ingest webhook-style debit transactions safely under network-retry duplication and concurrent load, without ever double-spending or allowing a negative balance.

## Stack

- Java 17+
- Spring Boot 4.1.x
- Spring Data JPA / Hibernate
- H2 (in-memory database — zero external setup required)
- JUnit 5

## Running the project

No external database, no config, no Postman required.

### Run the application
```bash
mvn spring-boot:run
```
The API will be available at `http://localhost:8080`.

### Run the tests
Open the project in IntelliJ and run `TransactionServiceIntegrationTest` directly — right-click the class → **Run**. All tests spin up a fresh embedded H2 instance and an embedded Tomcat server automatically; nothing needs to be started manually first.

Alternatively, from the command line:
```bash
mvn test
```

## API

### `POST /api/v1/transactions/process`

**Request body:**
```json
{
  "transactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa7",
  "amount": 250.00,
  "type": "DEBIT"
}
```

**Responses:**

| Scenario | Status | Notes |
|---|---|---|
| New transaction, processed successfully | `200 OK` | Wallet debited, ledger updated |
| Duplicate `transactionId` | `200 OK` | Cached original response replayed — balance untouched |
| Insufficient balance | `422 Unprocessable Entity` | No debit applied |
| Wallet not found | `404 Not Found` | |
| Invalid/missing request fields | `400 Bad Request` | Field-level validation errors |

## Core design decisions

Full reasoning, including where AI-assisted development went wrong and had to be corrected, is documented in [`DECISIONS.md`](./DECISIONS.md). Short version:

- **Idempotency** is enforced via a database unique constraint on `transactionId`, with the claim-attempt isolated in its own transaction (`Propagation.REQUIRES_NEW`) so a duplicate never corrupts the caller's persistence context.
- **Negative-balance prevention** is enforced via pessimistic row-level locking (`SELECT ... FOR UPDATE`) on the wallet row, serializing concurrent debits against the same wallet.

## Project structure

```
src/main/java/.../
├── controller/     # REST endpoint
├── service/        # business logic: idempotency claim + locking + debit
├── repository/     # Spring Data JPA repositories
├── entity/         # Wallet, TransactionRecord
├── enums/          # TransactionType, TransactionStatus
├── dto/            # TransactionRequest, TransactionResponse
└── exception/      # custom exceptions + global exception handler

src/test/java/.../
└── TransactionServiceIntegrationTest.java   # all 3 required test cases
```

## Test coverage

`TransactionServiceIntegrationTest` contains the three required cases:

1. **Happy path** — a single valid debit processes correctly and updates the balance.
2. **Idempotency** — 3 simultaneous requests with an identical `transactionId` result in exactly one real debit; the other two receive the replayed original response.
3. **Race condition** — 10 concurrent ₹100 debit requests against a ₹500 wallet result in exactly 5 successes and 5 insufficient-funds failures, with a final balance of exactly ₹0.00.

Each test uses `ExecutorService` + `CountDownLatch` to fire requests genuinely simultaneously against a live embedded server (`@SpringBootTest(webEnvironment = RANDOM_PORT)`), rather than simulating concurrency.us
├── dto/            # TransactionRequest, TransactionResponse
└── exception/      # custom exceptions + global exception handler

src/test/java/.../
└── TransactionServiceIntegrationTest.java   # all 3 required test cases
```

## Test coverage

`TransactionServiceIntegrationTest` contains the three required cases:

1. **Happy path** — a single valid debit processes correctly and updates the balance.
2. **Idempotency** — 3 simultaneous requests with an identical `transactionId` result in exactly one real debit; the other two receive the replayed original response.
3. **Race condition** — 10 concurrent ₹100 debit requests against a ₹500 wallet result in exactly 5 successes and 5 insufficient-funds failures, with a final balance of exactly ₹0.00.

Each test uses `ExecutorService` + `CountDownLatch` to fire requests genuinely simultaneously against a live embedded server (`@SpringBootTest(webEnvironment = RANDOM_PORT)`), rather than simulating concurrency.
