# Payment Service - Project Structure

Last updated: 2026-03-29

## Package layout

```text
com.payment.payment_service
|-- auth
|   |-- controller
|   |   `-- AuthController
|   |-- dto
|   |   |-- LoginRequest
|   |   `-- LoginResponse
|   |-- service
|   |   |-- AuthService
|   |   `-- JwtService
|   `-- JwtAuthenticationFilter
|
|-- config
|   |-- AuthenticatedUser
|   |-- GlobalExceptionHandler
|   |-- RateLimitConfig
|   |-- RateLimitFilter
|   |-- RateLimitProperties
|   |-- SecurityConfig
|   |-- SecurityUtils
|   `-- payment
|       `-- StripeConfig
|
|-- shared
|   |-- config
|   |   |-- KafkaConsumerConfig
|   |   `-- KafkaTopicsConfig
|   |-- crypto
|   |   |-- AesEncryptor
|   |   `-- HashUtil
|   |-- dto
|   |   |-- UserSummary
|   |   `-- WalletSummary
|   |-- entity
|   |   |-- BaseEntity
|   |   `-- OutboxEntity
|   |-- event
|   |   |-- DepositCompletedEvent
|   |   |-- TransferStatusChangedEvent
|   |   |-- UserCreatedEvent
|   |   |-- WalletCreditedEvent
|   |   `-- WalletDebitedEvent
|   |-- kafka
|   |   |-- KafkaEventProducer
|   |   `-- OutboxPublisher
|   |-- metrics
|   |   `-- PaymentMetrics
|   |-- query
|   |   |-- UserQueryService
|   |   `-- WalletQueryService
|   |-- repository
|   |   `-- OutboxRepository
|   `-- type
|       |-- TransferStatus
|       `-- TransferType
|
|-- user
|   |-- controller
|   |   `-- UserController
|   |-- converter
|   |   |-- DocumentConverter
|   |   `-- EmailConverter
|   |-- dto
|   |   |-- CreateUserRequestDTO
|   |   |-- PatchUserRequestDTO
|   |   `-- UserResponseDTO
|   |-- entity
|   |   `-- UserEntity
|   |-- exceptions
|   |   |-- UserDocumentException
|   |   |-- UserEmailException
|   |   |-- UserNotFoundException
|   |   `-- UserPasswordException
|   |-- repository
|   |   `-- UserRepository
|   |-- service
|   |   |-- CreateUserService
|   |   |-- DeleteUserService
|   |   |-- GetUserService
|   |   |-- PatchUserService
|   |   |-- UpdatePasswordService
|   |   |-- UpdateUserEmailService
|   |   `-- UserQueryServiceImpl
|   |-- type
|   |   `-- UserType
|   `-- value_object
|       |-- Document
|       |-- Email
|       `-- Password
|
|-- wallet
|   |-- consumer
|   |   |-- CreateWalletConsumer
|   |   `-- TransferWalletConsumer
|   |-- controller
|   |   |-- DepositController
|   |   `-- WalletController
|   |-- dto
|   |   |-- CreateDepositRequestDTO
|   |   |-- DepositResponseDTO
|   |   `-- WalletResponseDTO
|   |-- entity
|   |   |-- DepositEntity
|   |   |-- ProcessedTransferEntity
|   |   `-- WalletEntity
|   |-- exception
|   |   |-- InsufficientBalanceException
|   |   |-- InvalidPaymentProviderException
|   |   |-- PaymentProviderException
|   |   |-- WalletAlreadyExistsException
|   |   |-- WalletNotFoundException
|   |   `-- WebhookSignatureException
|   |-- provider
|   |   |-- PaymentProvider
|   |   |-- PaymentProviderResponse
|   |   |-- StripePaymentProvider
|   |   `-- WebhookResult
|   |-- repository
|   |   |-- DepositRepository
|   |   |-- ProcessedTransferRepository
|   |   `-- WalletRepository
|   |-- service
|   |   |-- CreateDepositService
|   |   |-- CreateWalletService
|   |   |-- GetWalletService
|   |   |-- ProcessDepositService
|   |   |-- ProcessTransferService
|   |   `-- WalletQueryServiceImpl
|   `-- type
|       |-- DepositStatus
|       `-- PaymentProviderName
|
|-- transfer
|   |-- consumer
|   |   `-- TransferStatusConsumer
|   |-- controller
|   |   `-- TransferController
|   |-- dto
|   |   |-- CreateTransferRequestDTO
|   |   |-- TransferFilterDTO
|   |   `-- TransferResponseDTO
|   |-- entity
|   |   `-- TransferEntity
|   |-- event
|   |   `-- TransferCreatedEvent
|   |-- exception
|   |   |-- TransferException
|   |   |-- TransferNotFoundException
|   |   `-- UnauthorizedTransferException
|   |-- repository
|   |   `-- TransferRepository (JpaSpecificationExecutor)
|   |-- service
|   |   |-- CreateTransferService
|   |   |-- GetTransferService
|   |   |-- TransferAuthorizationService
|   |   `-- TransferStatusUpdateService
|   `-- specification
|       `-- TransferSpecification
|
|-- transaction
|   |-- consumer
|   |   |-- DepositConsumer
|   |   `-- TransferConsumer
|   |-- controller
|   |   `-- TransactionController
|   |-- dto
|   |   `-- TransactionResponseDTO
|   |-- entity
|   |   `-- TransactionEntity
|   |-- exception
|   |   `-- TransactionNotFoundException
|   |-- repository
|   |   `-- TransactionRepository
|   |-- service
|   |   |-- CreateTransactionService
|   |   `-- GetTransactionService
|   `-- type
|       `-- TransactionType
|
`-- PaymentServiceApplication
|
`-- deposit (module - implemented within wallet)
    |-- consumer
    |   `-- DepositConsumer
```

## Runtime responsibilities

### `auth`

- Accepts login by email or document.
- Issues self-signed JWTs with JJWT.
- Revokes tokens on logout by storing the JWT ID in Redis until token expiry.
- Injects authenticated user context through `JwtAuthenticationFilter`.

### `config`

- Centralizes Spring Security, rate limiting, error handling, and ownership checks.
- `SecurityUtils.requireOwnership(...)` is the guard used by user, wallet, and transfer controllers.
- Rate limiting is optional and is attached after JWT authentication when enabled.

### `shared`

- Holds cross-cutting infrastructure and contracts.
- Defines Kafka topics, consumer error handling, shared events, and the outbox model.
- `OutboxPublisher` is the scheduled relay that pulls pending rows from `outbox`, publishes them to Kafka, and handles retries and cleanup.

### `user`

- Manages registration, retrieval, update, and deletion of users.
- Stores encrypted email and document values through JPA converters.
- Publishes `UserCreatedEvent` after persistence.

### `wallet`

- Creates one wallet per user.
- Processes transfer balance mutations with deterministic lock ordering.
- Uses `ProcessedTransferEntity` for idempotency.
- Writes debit and credit events to the outbox after a successful wallet mutation.
- Manages deposit creation and processing via payment providers.
- Handles webhooks from payment providers for deposit completion.
- Supports extensible payment provider interface (Stripe implemented).

### `transfer`

- Owns transfer creation and transfer status tracking.
- Validates sender, receiver, ownership, and balance preconditions before creating a transfer.
- Persists transfer creation as `PENDING` and records `TRANSFER_CREATED` in the outbox.
- Supports listing transfers by wallet with optional combinable filters: `status`, `type` (virtual DEBIT/CREDIT), `startDate`, `endDate`.
- Uses JPA Specification (`TransferSpecification`) to build dynamic queries with the filter combination.
- The `type` filter is virtual: `DEBIT` matches `source_wallet_id = walletId`, `CREDIT` matches `destination_wallet_id = walletId`.
- Response DTO includes a computed `type` field based on the requesting wallet's perspective.

### `transaction`

- Maintains the ledger of debit and credit entries.
- Reacts to wallet events and publishes the final transfer status event.
- Processes deposit completion events and persists credit ledger entries.
- Exposes query endpoints for transaction history with pagination and optional filters (walletId, transferId, type, date range).

## Public HTTP API

| Method | Route | Access | Notes |
|--------|-------|--------|-------|
| `POST` | `/api/v1/auth/login` | Public | Login with `identifier` + `password` |
| `POST` | `/api/v1/auth/logout` | Authenticated | Revokes current JWT via Redis blacklist |
| `POST` | `/api/v1/users` | Public | Creates a user; type is derived from CPF/CNPJ |
| `GET` | `/api/v1/users` | `ADMIN` only | Paginated list |
| `GET` | `/api/v1/users/{id}` | Owner or `ADMIN` | Ownership enforced in controller |
| `PATCH` | `/api/v1/users/{id}` | Owner or `ADMIN` | Updates email and/or password |
| `DELETE` | `/api/v1/users/{id}` | Owner or `ADMIN` | Soft/active logic is not used here; service deletes record |
| `GET` | `/api/v1/wallets/{userId}` | Owner or `ADMIN` | Returns the wallet for a user |
| `POST` | `/api/v1/transfers` | `COMMON` or `ADMIN` | Source wallet must belong to caller |
| `GET` | `/api/v1/transfers?walletId=...` | Owner or `ADMIN` | Paginated, sorted by `createdAt DESC`. Optional filters: `status`, `type` (DEBIT/CREDIT), `startDate`, `endDate` |
| `POST` | `/api/v1/deposits` | Authenticated | Creates deposit and returns checkout URL |
| `POST` | `/api/v1/deposits/webhook` | Public | Receives payment provider webhooks |
| `GET` | `/api/v1/deposits/{id}` | Owner or `ADMIN` | Returns deposit details |
| `GET` | `/api/v1/deposits?walletId=...` | Owner or `ADMIN` | Paginated deposit list |
| `GET` | `/api/v1/transactions?walletId=...` | Owner or `ADMIN` | Paginated, optional `type`, `startDate`, `endDate` filters |
| `GET` | `/api/v1/transactions?transferId=...` | Authenticated | All transactions for a given transfer |
| `GET` | `/api/v1/transactions/{id}` | Owner or `ADMIN` | Single transaction detail |
| `GET` | `/actuator/health` | Public | Health endpoint |

## Kafka topology

### Main topics

| Topic | Event types | Main producer(s) | Main consumer(s) |
|-------|-------------|------------------|------------------|
| `payment.users` | `UserCreatedEvent` | `CreateUserService` | `CreateWalletConsumer` |
| `payment.wallet.debits` | `WalletDebitedEvent` | `OutboxPublisher` | `transaction.TransferConsumer.consumeDebit(...)` |
| `payment.wallet.credits` | `WalletCreditedEvent` | `OutboxPublisher` | `transaction.TransferConsumer.consumeCredit(...)` |
| `payment.transfer.created` | `TransferCreatedEvent` | `OutboxPublisher` | `wallet.TransferWalletConsumer` |
| `payment.transfer.status` | `TransferStatusChangedEvent` | `KafkaEventProducer` from transaction/wallet flow | `transfer.TransferStatusConsumer` |
| `payment.deposit.completed` | `DepositCompletedEvent` | `OutboxPublisher` | `transaction.DepositConsumer` |

### Dead-letter topics

- Each main topic above also has a `.DLT` companion created by `KafkaTopicsConfig`.
- `KafkaConsumerConfig` uses `DefaultErrorHandler` plus `DeadLetterPublishingRecoverer`.
- Current retry policy is fixed backoff: 3 retries, 1 second apart, then DLT.

## Transfer lifecycle

```text
POST /api/v1/transfers
  -> TransferController
  -> SecurityUtils.requireOwnership(sourceWalletId)
  -> CreateTransferService
     -> TransferAuthorizationService.authorize(...)
     -> save TransferEntity(status=PENDING)
     -> save OutboxEntity(eventType=TRANSFER_CREATED)

Scheduled OutboxPublisher
  -> publish TransferCreatedEvent to payment.transfer.created

TransferWalletConsumer
  -> ProcessTransferService.execute(...)
     -> idempotency check via ProcessedTransferRepository
     -> deterministic wallet locking
     -> debit source wallet
     -> credit destination wallet
     -> save processed marker
     -> save outbox rows WALLET_DEBITED and WALLET_CREDITED

Scheduled OutboxPublisher
  -> publish wallet events

transaction.TransferConsumer
  -> persist debit and credit ledger rows
  -> publish TransferStatusChangedEvent(COMPLETED or FAILED)

transfer.TransferStatusConsumer
  -> update TransferEntity status idempotently
```

## Persistence model

| Entity | Purpose |
|--------|---------|
| `UserEntity` | Registered user account with encrypted PII and hashed password |
| `WalletEntity` | User balance container |
| `TransferEntity` | Transfer request and current status |
| `DepositEntity` | Deposit request with payment provider integration |
| `TransactionEntity` | Immutable ledger entry for debit/credit history |
| `ProcessedTransferEntity` | Idempotency marker for transfer processing |
| `OutboxEntity` | Pending/published integration event record |

## Test layout

```text
src/test/java/com/payment/payment_service
|-- config
|   |-- SecurityUtilsTest
|   `-- TestRedisConfig
|-- integration
|   |-- AbstractIntegrationTest
|   |-- AuthControllerIT
|   |-- TestHelper
|   |-- TransferControllerIT
|   |-- TransferFlowIT
|   `-- WalletControllerIT
|-- auth
|   |-- JwtAuthenticationFilterTest
|   `-- service
|       `-- JwtServiceTest
|-- transaction
|   |-- service
|   |   `-- CreateTransactionServiceTest
|   `-- consumer
|       |-- DepositConsumerTest
|       `-- TransferConsumerTest
|-- transfer
|   |-- service
|   |   |-- GetTransferServiceTest
|   |   `-- TransferAuthorizationServiceTest
|   `-- consumer
|       `-- TransferStatusConsumerTest
|-- user/controller
|   `-- UserControllerTest
|-- user/service
|   |-- CreateUserServiceTest
|   |-- DeleteUserServiceTest
|   |-- GetUserServiceTest
|   |-- PatchUserServiceTest
|   |-- UpdatePasswordServiceTest
|   `-- UpdateUserEmailServiceTest
`-- wallet
    |-- service
    |   |-- CreateWalletServiceTest
    |   `-- GetWalletServiceTest
    `-- consumer
        |-- CreateWalletConsumerTest
        `-- TransferWalletConsumerTest
```

## Current structural notes

- The project now uses an outbox table for transfer and wallet event publication. Old Spring event listener classes are no longer part of the design.
- Topic design is split by event type instead of multiplexing several event classes on the same topic.
- `user` exceptions use `exceptions`, while `transfer` and `wallet` use `exception`. The structure is intentionally documented as-is, not normalized.
- `UserType` includes `ADMIN`, but there is no public endpoint in this service that creates admin users.
- `TransferRepository` extends `JpaSpecificationExecutor` to support dynamic filter queries. The legacy `findBySourceWalletIdOrDestinationWalletId` query method is retained but no longer used by the listing endpoint.
- Composite indexes `(source_wallet_id, created_at DESC)` and `(destination_wallet_id, created_at DESC)` were added in `V3` migration for efficient filtered pagination.
