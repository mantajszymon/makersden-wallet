# Concurrent Ledger Service

A double-entry ledger with an HTTP API: create accounts, read balances, and post
transfers between accounts — safely under heavy concurrent load, and safely under
client retries.

## Run it

```bash
./mvnw spring-boot:run
```

Starts on `http://localhost:8080`. State is in memory only — it resets every time the service
restarts (see [Trade-offs](#trade-offs)).

## API

| Method & path | Body | Success | Notes |
|---|---|---|---|
| `POST /accounts` | `{"openingBalance": <number>}` | `201`, `Location` header, `{"id","balance"}` | balance must be ≥ 0 |
| `GET /accounts/{id}` | — | `200`, `{"id","balance"}` | `404` if unknown |
| `POST /transfers` | `{"fromAccountId","toAccountId","amount"}` + header `Idempotency-Key` (required) | `204` | see below |

An amount can be sent as a bare integer (`25`) or with up to 2 decimal places
(`25.50`) — anything with more than 2 decimal places (`1.005`) is rejected rather
than silently rounded.

### Idempotency key

There's no UI here, so it's worth being explicit: the **caller** generates the
`Idempotency-Key` — a UUID is the usual choice — once per *intended* transfer, and
resends that exact same key if it needs to retry (e.g. after a timeout where it
never saw the response). The server guarantees the transfer is applied **at most
once** per key, no matter how many times, or how concurrently, that key arrives —
including while the original request for that key is still being processed. A
retry with a different body under an already-used key still gets the *original*
outcome; the new body is ignored, not compared against the old one.

### Errors

| Situation | Status |
|---|---|
| Unknown account | `404` |
| Insufficient funds | `409` |
| Self-transfer, invalid/non-positive/over-precision amount, missing required field | `400` |

## Trade-offs

- **In-memory only, no database** — per the requirements. State is lost on
  restart
- **Idempotency keys are never evicted** — the store grows unboundedly over the
  service's lifetime. Fine for this exercise; a production version would need a
  TTL
- **A retry that arrives while the original is still in flight blocks the calling
  thread** until the original finishes, rather than returning a "still processing"
  status. Simpler, and matches "a repeated key returns the original outcome," but
  ties up a request thread for the original's duration.
- **Lock-based concurrency, not lock-free** — a `ReentrantLock` per account, always
  acquired in a fixed order so two transfers racing over the same pair of accounts can never deadlock
- **Account ids are a short, sequential counter, not random** — chosen for
  readability (`"43"` over a UUID) at the cost of being enumerable.
  Acceptable for this exercise; a public-facing production API would want
  opaque or non-sequential ids instead
- **Out of scope**: authentication/authorization, multi-currency support, and any
  persistence/audit trail beyond current balances.

## Project layout

```
core/            ledger domain: plain Java, no Spring, unit-testable without HTTP
  model/         Account — balances, per-account locking, transfer logic
  repository/    AccountRepository / IdempotencyKeyStore + in-memory implementations
  service/       LedgerService — the application-facing API
  exception/     domain exceptions
  util/          MoneyValidation + TransferValidation

api/             HTTP layer: Spring MVC
  controller/    AccountController, TransferController
  dto/           request/response records
  exception/     maps domain exceptions to ProblemDetail responses

config/          Spring @Configuration wiring the core into beans
```

`LedgerService` and everything under `core/` know nothing about Spring or HTTP —
only `config/` and `api/` do.
