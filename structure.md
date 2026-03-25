# Payment Service - Estrutura do Projeto

## 📁 Estrutura de Pacotes

```
com.payment.payment_service
├── shared                          ← Contratos, eventos e infraestrutura transversal
│   ├── config
│   │   ├── KafkaTopicsConfig       ← Configuração de tópicos Kafka
│   │   └── KafkaConsumerConfig     ← Configuração de consumers Kafka
│   ├── crypto
│   │   ├── AesEncryptor           ← Criptografia AES-256-CBC
│   │   └── HashUtil               ← Hash SHA-256
│   ├── dto
│   │   ├── UserSummary            ← DTO resumido de usuário
│   │   └── WalletSummary          ← DTO resumido de carteira
│   ├── entity
│   │   └── BaseEntity             ← Entidade base com timestamps
│   ├── event
│   │   ├── UserCreatedEvent       ← Evento de criação de usuário
│   │   ├── WalletDebitedEvent    ← Evento de débito em carteira
│   │   ├── WalletCreditedEvent   ← Evento de crédito em carteira
│   │   └── TransferStatusChangedEvent ← Evento de mudança de status
│   ├── kafka
│   │   └── KafkaEventProducer     ← Produtor de eventos Kafka
│   ├── query
│   │   ├── UserQueryService      ← Interface de consulta de usuário
│   │   └── WalletQueryService    ← Interface de consulta de carteira
│   └── type
│       └── TransferStatus         ← Enum de status de transferência
│
├── user                            ← Contexto de Usuários
│   ├── controller
│   │   └── UserController         ← REST endpoints para usuários
│   ├── service
│   │   ├── CreateUserService      ← Criação de usuário
│   │   ├── DeleteUserService      ← Remoção de usuário
│   │   ├── GetUserService         ← Consulta de usuário
│   │   ├── PatchUserService       ← Atualização parcial de usuário
│   │   ├── UpdatePasswordService  ← Atualização de senha
│   │   ├── UpdateUserEmailService ← Atualização de e-mail
│   │   └── UserQueryServiceImpl   ← Implementação de UserQueryService
│   ├── repository
│   │   └── UserRepository         ← Repositório JPA de usuários
│   ├── entity
│   │   └── UserEntity            ← Entidade de usuário
│   ├── dto
│   │   ├── CreateUserRequestDTO  ← DTO para criação
│   │   ├── PatchUserRequestDTO   ← DTO para atualização parcial
│   │   └── UserResponseDTO       ← DTO de resposta
│   ├── exception
│   │   ├── UserDocumentException  ← Erro de documento duplicado
│   │   ├── UserEmailException    ← Erro de e-mail duplicado
│   │   ├── UserNotFoundException  ← Usuário não encontrado
│   │   └── UserPasswordException ← Erro de validação de senha
│   ├── type
│   │   └── UserType               ← Enum (COMMON, MERCHANT)
│   ├── value_object
│   │   ├── Document              ← Value Object para documento
│   │   ├── Email                  ← Value Object para e-mail
│   │   └── Password               ← Value Object para senha
│   └── converter
│       ├── DocumentConverter      ← JPA converter para documento
│       └── EmailConverter         ← JPA converter para e-mail
│
├── wallet                          ← Contexto de Carteiras
│   ├── controller
│   │   └── WalletController       ← REST endpoints para carteiras
│   ├── service
│   │   ├── CreateWalletService    ← Criação de carteira
│   │   ├── GetWalletService       ← Consulta de carteira
│   │   ├── ProcessTransferService ← Processamento de transferências
│   │   └── WalletQueryServiceImpl ← Implementação de WalletQueryService
│   ├── repository
│   │   ├── WalletRepository       ← Repositório JPA de carteiras
│   │   └── ProcessedTransferRepository ← Controle de idempotência
│   ├── entity
│   │   ├── WalletEntity           ← Entidade de carteira
│   │   └── ProcessedTransferEntity ← Controle de processamento
│   ├── dto
│   │   └── WalletResponseDTO      ← DTO de resposta
│   ├── exception
│   │   ├── WalletAlreadyExistsException ← Carteira já existe
│   │   ├── WalletNotFoundException    ← Carteira não encontrada
│   │   └── InsufficientBalanceException ← Saldo insuficiente
│   └── consumer
│       ├── CreateWalletConsumer   ← Consome UserCreatedEvent
│       └── TransferWalletConsumer ← Consome TransferCreatedEvent
│
├── transfer                        ← Contexto de Transferências
│   ├── controller
│   │   └── TransactionController   ← REST endpoints para transferências
│   ├── service
│   │   ├── CreateTransferService   ← Criação de transferência
│   │   ├── GetTransferService      ← Consulta de transferência
│   │   ├── TransferAuthorizationService ← Autorização de transferência
│   │   └── TransferStatusUpdateService ← Atualização de status
│   ├── repository
│   │   └── TransferRepository      ← Repositório JPA de transferências
│   ├── entity
│   │   └── TransferEntity         ← Entidade de transferência
│   ├── dto
│   │   ├── CreateTransferRequestDTO ← DTO para criação
│   │   └── TransferResponseDTO      ← DTO de resposta
│   ├── exception
│   │   ├── TransferException        ← Erro genérico de transferência
│   │   ├── InvalidTransferException ← Transferência inválida
│   │   ├── UnauthorizedTransferException ← Transferência não autorizada
│   │   └── TransferNotFoundException ← Transferência não encontrada
│   ├── listener
│   │   ├── TransferCreatedListener ← Listener de Spring Events
│   │   └── TransferPublishService  ← Publicação de eventos Kafka
│   ├── event
│   │   └── TransferCreatedEvent    ← Evento de criação de transferência
│   └── consumer
│       └── TransferStatusConsumer  ← Consome TransferStatusChangedEvent
│
├── transaction                     ← Contexto de Transações (Ledger)
│   ├── service
│   │   └── CreateTransactionService ← Criação de transações
│   ├── repository
│   │   └── TransactionRepository     ← Repositório JPA de transações
│   ├── entity
│   │   └── TransactionEntity        ← Entidade de transação
│   ├── type
│   │   └── TransactionType          ← Enum (DEBIT, CREDIT)
│   └── consumer
│       └── TransferConsumer         ← Consome eventos de wallet
│
├── config
│   └── SecurityConfig              ← Configuração de segurança Spring
│
└── PaymentServiceApplication       ← Classe principal Spring Boot
```

---

## 🌐 API Endpoints

### 👥 Usuários

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/v1/users` | Cria novo usuário (tipo derivado do documento) |
| `GET` | `/api/v1/users` | Lista todos os usuários (documento mascarado) |
| `GET` | `/api/v1/users/{id}` | Retorna usuário por ID |
| `PATCH` | `/api/v1/users/{id}` | Atualiza e-mail e/ou senha |
| `DELETE` | `/api/v1/users/{id}` | Remove usuário por ID |

### 💰 Carteiras

| Método | Rota | Descrição |
|--------|------|-----------|
| `GET` | `/api/v1/wallets/{userId}` | Retorna carteira do usuário |

### 🔄 Transferências

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/v1/transfers` | Inicia nova transferência |
| `GET` | `/api/v1/transfers?walletId={id}` | Lista transferências da carteira (paginado) |

---

## 🐙 Kafka Topics

### Tópicos Principais

| Tópico | Eventos | Produzido por | Consumido por |
|--------|---------|---------------|---------------|
| `payment.users` | `UserCreatedEvent` | `CreateUserService` | `CreateWalletConsumer` |
| `payment.wallets` | `WalletDebitedEvent`, `WalletCreditedEvent` | `ProcessTransferService` | `TransferConsumer` |
| `payment.transfers` | `TransferCreatedEvent`, `TransferStatusChangedEvent` | `TransferPublishService`, `TransferConsumer` | `TransferWalletConsumer`, `TransferStatusConsumer` |

### Dead Letter Topics

| Tópico | Uso |
|--------|-----|
| `payment.users.DLT` | Mensagens com falha em `payment.users` |
| `payment.wallets.DLT` | Mensagens com falha em `payment.wallets` |
| `payment.transfers.DLT` | Mensagens com falha em `payment.transfers` |

---

## 🔄 Fluxo de Eventos

### Fluxo de Criação de Usuário

```
POST /api/v1/users
  → CreateUserService.execute()
    → UserRepository.save()
    → kafkaEventProducer.publishUserCreated()
      → UserCreatedEvent (Kafka)
        → CreateWalletConsumer.consume()
          → CreateWalletService.execute()
```

### Fluxo de Transferência

```
POST /api/v1/transfers
  → CreateTransferService.execute()
    → TransferAuthorizationService.authorize()
    → TransferRepository.save() (status: PENDING)
    → eventPublisher.publishEvent() (Spring Event)
      → TransferCreatedListener.handle()
        → TransferPublishService.publish() (Kafka)
          → TransferCreatedEvent (Kafka)
            → TransferWalletConsumer.consume()
              → ProcessTransferService.execute()
                → Lock pessimista determinístico
                → Débito e crédito atômicos
                → kafkaEventProducer.publishWalletDebited()
                → kafkaEventProducer.publishWalletCredited()
                  → WalletDebitedEvent (Kafka)
                    → TransferConsumer.consume()
                      → CreateTransactionService.executeDebit()
                  → WalletCreditedEvent (Kafka)
                    → TransferConsumer.consume()
                      → CreateTransactionService.executeCredit()
                  → kafkaEventProducer.publishTransferStatusChanged() (COMPLETED)
                    → TransferStatusChangedEvent (Kafka)
                      → TransferStatusConsumer.consume()
                        → TransferRepository.updateStatus()
```

---

## 🗄️ Entidades de Banco

### UserEntity

```java
- id: UUID
- name: String
- email: String (encrypted)
- password: String (BCrypt)
- document: String (AES-256-CBC)
- document_hash: String (SHA-256)
- type: UserType (COMMON/MERCHANT)
- active: Boolean
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

### WalletEntity

```java
- id: UUID
- userId: UUID
- balance: BigDecimal
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

### TransferEntity

```java
- id: UUID
- sourceWalletId: UUID
- destinationWalletId: UUID
- amount: BigDecimal
- status: TransferStatus (PENDING/COMPLETED/FAILED)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

### TransactionEntity

```java
- id: UUID
- walletId: UUID
- transferId: UUID
- type: TransactionType (DEBIT/CREDIT)
- amount: BigDecimal
- createdAt: LocalDateTime (imutável)
```

### ProcessedTransferEntity

```java
- id: UUID
- createdAt: LocalDateTime
```

---

## 🎯 Padrões e Convenções

### Value Objects

- **Document**: Valida CPF/CNPJ e deriva `UserType`
- **Email**: Valida formato de e-mail
- **Password**: Valida requisitos de segurança

### JPA Converters

- **DocumentConverter**: Criptografa/Descriptografa documentos
- **EmailConverter**: Criptografa/Descriptografa e-mails

### Idempotência

- `ProcessedTransferRepository`: Garante processamento único de transferências
- `TransferStatusConsumer`: Verifica status antes de atualizar

### Lock Strategy

- Lock pessimista determinístico (menor UUID primeiro)
- Previne deadlocks matematicamente
- Permite concorrência em transferências não conflitantes

### Retry Pattern

- 3 tentativas com backoff exponencial (1s, 2s, 4s)
- Fallback para status FAILED ao esgotar tentativas

---

## 🔐 Segurança

### Criptografia

- **AES-256-CBC**: Documentos e e-mails em repouso
- **BCrypt**: Senhas
- **SHA-256**: Hash para unicidade de documentos

### Autorização

- `COMMON`: Pode enviar e receber transferências
- `MERCHANT`: Apenas pode receber transferências

---

## 📊 Testes

### Estrutura de Testes

```
src/test/java/com/payment/payment_service
├── user
│   ├── controller
│   │   └── UserControllerTest
│   └── service
│       ├── CreateUserServiceTest
│       ├── DeleteUserServiceTest
│       ├── GetUserServiceTest
│       ├── PatchUserServiceTest
│       ├── UpdatePasswordServiceTest
│       └── UpdateUserEmailServiceTest
├── wallet
│   └── service
│       ├── CreateWalletServiceTest
│       └── GetWalletServiceTest
├── transfer
│   └── service
│       ├── TransferAuthorizationServiceTest
│       └── GetTransferServiceTest
└── transaction
    └── service
        └── CreateTransactionServiceTest
```

---

## 🚀 Infraestrutura

### Docker Compose

```yaml
services:
  - postgres: PostgreSQL 16
  - zookeeper: Kafka dependency
  - kafka: Apache Kafka 7.5
  - payment-service: Application
```

### Configuração

- **Database**: PostgreSQL 16 via JPA/Hibernate
- **Migrations**: Flyway
- **Message Broker**: Apache Kafka 7.5
- **Actuator**: Health checks e metrics
- **Build**: Maven
