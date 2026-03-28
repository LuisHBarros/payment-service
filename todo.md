# Project TODO - Payment Service

Last updated: 2026-03-28

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

---

## Critical Issues

No unresolved critical issues from the initial review.

---

## High Priority

| # | Issue | Status | Notes |
|---|-------|--------|-------|
| 1 | Missing CORS configuration | OPEN | No `CorsConfigurationSource`, `@CrossOrigin`, or MVC CORS config is present |
| 2 | Auth/security test coverage is still incomplete | OPEN | `AuthControllerIT` exists, but there are still no direct tests for `JwtService`, `JwtAuthenticationFilter`, `RateLimitFilter`, or `SecurityUtils` |
| 3 | Kafka consumer and outbox flow tests are still missing | OPEN | No focused tests for `TransferConsumer`, `TransferStatusConsumer`, `TransferWalletConsumer`, or `OutboxPublisher` |
| 4 | CI only runs `mvn test` | OPEN | `.github/workflows/ci.yml` does not run `mvn verify`, so integration tests are not exercised in GitHub Actions |

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
| 12 | JWT expiration mismatch between config files | OPEN | `application.yaml` uses `86400000` ms while `.env.example` still says `86400` |

---

## Test Coverage Snapshot

| Area | Status | Notes |
|------|--------|-------|
| user services | GOOD | Existing unit tests cover core user service flows |
| wallet services | GOOD | Core wallet retrieval/creation logic is covered |
| transfer services | GOOD | `GetTransferServiceTest` and `TransferAuthorizationServiceTest` exist |
| auth | MODERATE | `AuthControllerIT` exists, but lower-level JWT/filter coverage is missing |
| controller integration | MODERATE | Basic integration suite exists for auth, transfers, wallet, and happy-path flow |
| Kafka consumers | LOW | No dedicated listener tests |
| Kafka producer / outbox | LOW | No direct tests for producer callback behavior or outbox polling/cleanup |
| rate limiting | LOW | No focused tests for throttling behavior |
| authorization helpers | LOW | No direct tests for `SecurityUtils` ownership checks |

---

## Next Recommended Work

1. Add CORS configuration and document the allowed origins strategy.
2. Add focused tests for JWT auth, rate limiting, ownership checks, and Kafka listeners.
3. Change CI from `mvn test` to `mvn verify` so integration tests run in GitHub Actions.
4. Fix the JWT expiration mismatch in `.env.example`.
5. Replace AES-CBC/default-charset usage in `AesEncryptor`.
