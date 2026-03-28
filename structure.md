# Payment Service - Project Structure

Last updated: 2026-03-28

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
|   `-- SecurityUtils
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
|   |   |-- TransferStatusChangedEvent
|   |   |-- UserCreatedEvent
|   |   |-- WalletCreditedEvent
|   |   `-- WalletDebitedEvent
|   |-- kafka
|   |   |-- KafkaEventProducer
|   |   `-- OutboxPublisher
|   |-- query
|   |   |-- UserQueryService
|   |   `-- WalletQueryService
|   |-- repository
|   |   `-- OutboxRepository
|   `-- type
|       `-- TransferStatus
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
|   |   `-- WalletController
|   |-- dto
|   |   `-- WalletResponseDTO
|   |-- entity
|   |   |-- ProcessedTransferEntity
|   |   `-- WalletEntity
|   |-- exception
|   |   |-- InsufficientBalanceException
|   |   |-- WalletAlreadyExistsException
|   |   `-- WalletNotFoundException
|   |-- repository
|   |   |-- ProcessedTransferRepository
|   |   `-- WalletRepository
|   `-- service
|       |-- CreateWalletService
|       |-- GetWalletService
|       |-- ProcessTransferService
|       `-- WalletQueryServiceImpl
|
|-- transfer
|   |-- consumer
|   |   `-- TransferStatusConsumer
|   |-- controller
|   |   `-- TransferController
|   |-- dto
|   |   |-- CreateTransferRequestDTO
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
|   |   `-- TransferRepository
|   `-- service
|       |-- CreateTransferService
|       |-- GetTransferService
|       |-- TransferAuthorizationService
|       `-- TransferStatusUpdateService
|
|-- transaction
|   |-- consumer
|   |   `-- TransferConsumer
|   |-- entity
|   |   `-- TransactionEntity
|   |-- repository
|   |   `-- TransactionRepository
|   |-- service
|   |   `-- CreateTransactionService
|   `-- type
|       `-- TransactionType
|
`-- PaymentServiceApplication
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

### `transfer`

- Owns transfer creation and transfer status tracking.
- Validates sender, receiver, ownership, and balance preconditions before creating a transfer.
- Persists transfer creation as `PENDING` and records `TRANSFER_CREATED` in the outbox.

### `transaction`

- Maintains the ledger of debit and credit entries.
- Reacts to wallet events and publishes the final transfer status event.

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
| `GET` | `/api/v1/transfers?walletId=...` | Owner or `ADMIN` | Paginated, sorted by `createdAt DESC` |
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
| `TransactionEntity` | Immutable ledger entry for debit/credit history |
| `ProcessedTransferEntity` | Idempotency marker for transfer processing |
| `OutboxEntity` | Pending/published integration event record |

## Test layout

```text
src/test/java/com/payment/payment_service
|-- config
|   `-- TestRedisConfig
|-- integration
|   |-- AbstractIntegrationTest
|   |-- AuthControllerIT
|   |-- TestHelper
|   |-- TransferControllerIT
|   |-- TransferFlowIT
|   `-- WalletControllerIT
|-- transaction/service
|   `-- CreateTransactionServiceTest
|-- transfer/service
|   |-- GetTransferServiceTest
|   `-- TransferAuthorizationServiceTest
|-- user/controller
|   `-- UserControllerTest
|-- user/service
|   |-- CreateUserServiceTest
|   |-- DeleteUserServiceTest
|   |-- GetUserServiceTest
|   |-- PatchUserServiceTest
|   |-- UpdatePasswordServiceTest
|   `-- UpdateUserEmailServiceTest
`-- wallet/service
    |-- CreateWalletServiceTest
    `-- GetWalletServiceTest
```

## Current structural notes

- The project now uses an outbox table for transfer and wallet event publication. Old Spring event listener classes are no longer part of the design.
- Topic design is split by event type instead of multiplexing several event classes on the same topic.
- `user` exceptions use `exceptions`, while `transfer` and `wallet` use `exception`. The structure is intentionally documented as-is, not normalized.
- `UserType` includes `ADMIN`, but there is no public endpoint in this service that creates admin users.
