# Payment Service - Structure

Last updated: 2026-03-29

## Package layout

```text
com.payment.payment_service
|-- auth
|-- config
|-- shared
|-- transaction
|-- transfer
|-- user
|-- wallet
`-- PaymentServiceApplication
```

## Module responsibilities

### `auth`

- `AuthController` exposes login, logout and `me`.
- `AuthService` authenticates by email or document.
- `JwtService` issues tokens and validates expiration claims.
- `JwtAuthenticationFilter` resolves the authenticated principal for protected routes.

### `config`

- `SecurityConfig` defines public routes, JWT filter order, CORS and stateless session policy.
- `RateLimitConfig`, `RateLimitFilter` and `RateLimitProperties` provide optional throttling.
- `GlobalExceptionHandler` maps domain exceptions to HTTP responses.
- `SecurityUtils.requireOwnership(...)` is the standard ownership guard across controllers.

### `shared`

- `shared/config` contains Kafka consumer and topic configuration.
- `shared/entity/OutboxEntity` and `shared/repository/OutboxRepository` back the outbox table.
- `shared/kafka/KafkaEventProducer` sends typed events to Kafka.
- `shared/kafka/OutboxPublisher` polls pending rows, waits for Kafka acks, records metrics and performs recovery/cleanup.
- `shared/metrics/PaymentMetrics` centralizes custom Micrometer counters and timers.
- `shared/query` exposes cross-module read contracts used by controllers and services.

### `user`

- Persists users with encrypted email and document converters.
- Derives `UserType` from CPF or CNPJ.
- Publishes `UserCreatedEvent` after user creation.

### `wallet`

- `CreateWalletConsumer` creates one wallet per user from `UserCreatedEvent`.
- `ProcessTransferService` performs balance mutation with deterministic lock ordering.
- `ProcessedTransferEntity` prevents duplicate transfer processing.
- `DepositController` manages deposit creation, listing and webhook handling.
- `provider/StripePaymentProvider` is the only implemented payment provider today.

### `transfer`

- `CreateTransferService` validates business preconditions, persists `TransferEntity` as `PENDING` and writes `TRANSFER_CREATED` to outbox.
- `GetTransferService` queries transfers with optional filters.
- `TransferStatusConsumer` consumes status updates and keeps `TransferEntity` synchronized.

### `transaction`

- `TransferConsumer` writes debit and credit ledger entries from wallet events.
- `DepositConsumer` writes `CREDIT` entries for successful deposits.
- `TransactionController` exposes paginated ledger queries by wallet or transfer.

## HTTP surface

| Method | Route | Access | Notes |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Public | Login with `identifier` + `password` |
| `GET` | `/api/v1/auth/me` | Authenticated | Returns current user |
| `POST` | `/api/v1/auth/logout` | Authenticated | Blacklists current JWT |
| `POST` | `/api/v1/users` | Public | Creates user |
| `GET` | `/api/v1/users` | `ADMIN` | Paginated list |
| `GET` | `/api/v1/users/{id}` | Owner or `ADMIN` | Ownership check in controller |
| `PATCH` | `/api/v1/users/{id}` | Owner or `ADMIN` | Partial update |
| `DELETE` | `/api/v1/users/{id}` | Owner or `ADMIN` | Deletes record |
| `GET` | `/api/v1/wallets/{userId}` | Owner or `ADMIN` | Returns wallet by user |
| `POST` | `/api/v1/wallets/{userId}/deposits` | Owner or `ADMIN` | Creates deposit |
| `GET` | `/api/v1/wallets/{userId}/deposits` | Owner or `ADMIN` | Lists deposits |
| `POST` | `/api/v1/webhooks/deposits` | Public | Validates provider signature |
| `POST` | `/api/v1/transfers` | `COMMON` or `ADMIN` | Creates transfer |
| `GET` | `/api/v1/transfers` | Authenticated | Requires `walletId`; optional filters |
| `GET` | `/api/v1/transactions` | Authenticated | Query by `walletId` or `transferId` |
| `GET` | `/api/v1/transactions/{id}` | Authenticated | Returns transaction by id |
| `GET` | `/actuator/health` | Public | Health endpoint |
| `GET` | `/actuator/info` | Public | Info endpoint |
| `GET` | `/actuator/metrics` | Public | Metrics index |
| `GET` | `/actuator/prometheus` | Public | Prometheus scrape endpoint |

## Kafka topics and async handlers

| Topic | Event | Producer | Consumer |
|---|---|---|---|
| `payment.users` | `UserCreatedEvent` | user module via outbox | `wallet.CreateWalletConsumer` |
| `payment.transfer.created` | `TransferCreatedEvent` | transfer module via outbox | `wallet.TransferWalletConsumer` |
| `payment.wallet.debits` | `WalletDebitedEvent` | wallet module via outbox | `transaction.TransferConsumer` |
| `payment.wallet.credits` | `WalletCreditedEvent` | wallet module via outbox | `transaction.TransferConsumer` |
| `payment.transfer.status` | `TransferStatusChangedEvent` | transaction module via outbox | `transfer.TransferStatusConsumer` |
| `payment.deposit.completed` | `DepositCompletedEvent` | wallet module via outbox | `transaction.DepositConsumer` |

## Runtime rules worth preserving

- Ownership is enforced through `SecurityUtils.requireOwnership(...)`, not by repository filtering.
- Rate limiting is optional and attached only when the filter bean is available.
- Kafka consumer retries are configured in `KafkaConsumerConfig` with `FixedBackOff` before DLT publishing.
- Outbox relay waits synchronously for Kafka ack per record and marks exhausted records as processed after recovery.
- Stripe deposit creation uses Resilience4j retry and circuit breaker with a shared fallback.
- `PaymentProviderName` currently supports only `STRIPE`.

## Operational files

| File | Purpose |
|---|---|
| `src/main/resources/application.yaml` | Main runtime configuration, Kafka, Redis, rate limit and Resilience4j |
| `src/main/resources/logback-spring.xml` | Console logging pattern |
| `docker-compose.yml` | Local stack with app, postgres, kafka, redis, prometheus and grafana |
| `prometheus.yml` | Scrape config for `payment-service:8080/actuator/prometheus` |
| `grafana/provisioning/datasources/prometheus.yml` | Grafana datasource provisioning |
| `grafana/provisioning/dashboards/dashboards.yml` | Grafana dashboard provisioning |
| `seed.sh` | Local seed flow with sample users and transfers |
| `load.sh` | Simple transfer load generator |
