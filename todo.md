# Project TODO - Payment Service

Last updated: 2026-03-29

## Recently Completed

- Implemented JWT authentication and Redis-backed token blacklist for logout.
- Added ownership checks and role-based authorization across user, wallet, and transfer endpoints.
- Added rate limiting with Bucket4j + Redis for public and authenticated routes.
- Added `GlobalExceptionHandler` and replaced raw runtime failures with domain exceptions where already identified.
- Implemented the outbox pattern for Kafka publishing, including polling, retry limits, and cleanup.
- Added Flyway migration `V1__init_schema.sql` for the initial schema and indexes.
- Added pagination for `GET /api/v1/users`.
- Renamed `TransactionController` to `TransferController`.
- Added Docker-related cleanup: `.dockerignore` and externalized compose credentials.
- Added GitHub Actions CI at `.github/workflows/ci.yml`.
- Added integration-test scaffolding with Testcontainers:
  `AuthControllerIT`, `TransferControllerIT`, `TransferFlowIT`, `WalletControllerIT`.
- Kafka producer now handles async send results with `.whenComplete(...)` logging.
- Kafka consumers are now split by event type/topic instead of relying on shared-topic branching.
- `RateLimitProperties.getEndpointLimit(...)` now returns `null` for unmapped routes.
- `@Transactional` usage is now consistently Spring-based.
- Implemented deposit module with payment provider integration (Stripe).
- Added webhook signature verification for payment providers.
- Created observability stack with Prometheus and Grafana.
- Added custom metrics for transfers, deposits, and wallet operations.
- Implemented JSON structured logging with Logback.
- Added database seeding and load testing scripts.
- Implemented transaction ledger query endpoints (`GET /api/v1/transactions`) with pagination, filters (walletId, transferId, type, date range), and ownership checks.

---

## Critical Issues

No unresolved critical issues.

---

## High Priority

| # | Issue | Status | Notes |
|---|-------|--------|-------|
| 1 | Missing CORS configuration | OPEN | No `CorsConfigurationSource`, `@CrossOrigin`, or MVC CORS config is present |
| 2 | Auth/security test coverage is still incomplete | OPEN | `AuthControllerIT` exists, but there are still no direct tests for `JwtService`, `JwtAuthenticationFilter`, `RateLimitFilter`, or `SecurityUtils` |
| 3 | Kafka consumer and outbox flow tests are still missing | OPEN | No focused tests for `TransferConsumer`, `TransferStatusConsumer`, `TransferWalletConsumer`, `DepositConsumer`, or `OutboxPublisher` |
| 4 | CI only runs `mvn test` | OPEN | `.github/workflows/ci.yml` does not run `mvn verify`, so integration tests are not exercised in GitHub Actions |
| 5 | Deposit webhook security needs validation | OPEN | Webhook signature verification exists but needs more thorough testing |
| 6 | Payment provider error handling needs improvement | OPEN | Better recovery strategies for failed payment provider calls |

---

## Medium Priority

| # | Issue | Status | Notes |
|---|-------|--------|-------|
| 5 | Password rules are still weak | OPEN | `Password` only enforces non-blank plus minimum length of 5 |
| 6 | `AesEncryptor` still uses AES-CBC | OPEN | Consider AES-GCM for authenticated encryption |
| 7 | `AesEncryptor` still uses platform-default charset | OPEN | `secret.getBytes()` and `plainText.getBytes()` should use `StandardCharsets.UTF_8` |
| 8 | Mixed dependency-injection styles across services/controllers | OPEN | The codebase still mixes explicit constructors and Lombok `@RequiredArgsConstructor` |
| 9 | Exception package naming is inconsistent | OPEN | `user.exceptions` vs `transfer.exception` / `wallet.exception` |
| 10 | `TransferAuthorizationServiceTest` naming/formatting needs cleanup | OPEN | Test names are reasonable now, but formatting is inconsistent and readability is still weak |
| 11 | Retry/backoff values are still partly hardcoded | OPEN | Example: Kafka consumer retry settings are still embedded in config code/comments |
| 12 | Additional payment providers needed | OPEN | Currently only Stripe is implemented; consider PayPal, Mercado Pago, etc. |
| 13 | Grafana dashboards need customization | OPEN | Default dashboards are functional but could be improved for better insights |
| 14 | Prometheus scrape interval configuration | OPEN | Default interval may not be optimal for all environments |
| 15 | Deposit notification system | OPEN | Users should be notified when deposits complete or fail |

---

## Test Coverage Snapshot

| Area | Status | Notes |
|------|--------|-------|
| user services | GOOD | Existing unit tests cover core user service flows |
| wallet services | GOOD | Core wallet retrieval/creation logic is covered |
| transfer services | GOOD | `GetTransferServiceTest` and `TransferAuthorizationServiceTest` exist |
| deposit services | MODERATE | Basic tests for deposit creation and processing exist |
| auth | MODERATE | `AuthControllerIT` exists, but lower-level JWT/filter coverage is missing |
| controller integration | MODERATE | Basic integration suite exists for auth, transfers, wallet, and happy-path flow |
| payment providers | MODERATE | Tests for Stripe payment provider exist |
| Kafka consumers | LOW | No dedicated listener tests |
| Kafka producer / outbox | LOW | No direct tests for producer callback behavior or outbox polling/cleanup |
| rate limiting | LOW | No focused tests for throttling behavior |
| authorization helpers | LOW | No direct tests for `SecurityUtils` ownership checks |
| observability | LOW | No tests for metrics collection and logging |

---

## Next Recommended Work

1. Add CORS configuration and document the allowed origins strategy.
2. Add focused tests for JWT auth, rate limiting, ownership checks, and Kafka listeners.
3. Change CI from `mvn test` to `mvn verify` so integration tests run in GitHub Actions.
4. Improve deposit webhook security validation and testing.
5. Enhance payment provider error handling and recovery strategies.
6. Implement additional payment providers (PayPal, Mercado Pago, etc.).
7. Customize Grafana dashboards for better insights.
8. Implement deposit notification system (email, push, etc.).
