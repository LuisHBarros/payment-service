# payment-service

API REST para gerenciamento de pagamentos com arquitetura baseada em eventos · Java 21 · Spring Boot 3

---

## 📋 Visão Geral

O **payment-service** é uma API REST que gerencia usuários, carteiras digitais e transferências financeiras entre pessoas físicas (COMMON) e lojistas (MERCHANT), seguindo os princípios de **Domain-Driven Design (DDD)** e **Event-Driven Architecture (EDA)**.

A aplicação utiliza **Kafka** para comunicação assíncrona entre contextos de domínio, garantindo desacoplamento e escalabilidade. O fluxo de transferência implementa **lock pessimista determinístico**, **idempotência** e **retry com backoff** para garantir consistência em operações financeiras críticas.

| | |
|---|---|
| **Linguagem** | Java 21 |
| **Framework** | Spring Boot 3.5 |
| **Banco de dados** | PostgreSQL 16 |
| **Mensageria** | Apache Kafka 7.5 |
| **Segurança** | AES-256-CBC + BCrypt |
| **Validação** | Bean Validation + Value Objects |
| **Build** | Maven |
| **Migrações** | Flyway |

---

## 🏗️ Arquitetura

### Contextos de Domínio

```
payment_service
├── shared          ← contratos, eventos, infraestrutura transversal
├── user            ← cadastro e gestão de usuários
├── wallet          ← carteiras digitais e processamento de transferências
├── transfer        ← orquestração de transferências
└── transaction     ├── ledger imutável de movimentações
```

### Estrutura do `shared`

```
shared
├── config
│   ├── KafkaTopicsConfig
│   └── KafkaConsumerConfig
├── crypto
│   ├── AesEncryptor
│   └── HashUtil
├── dto
│   ├── UserSummary
│   └── WalletSummary
├── entity
│   └── BaseEntity
├── event
│   ├── UserCreatedEvent
│   ├── WalletDebitedEvent
│   ├── WalletCreditedEvent
│   └── TransferStatusChangedEvent
├── kafka
│   └── KafkaEventProducer
├── query
│   ├── UserQueryService
│   └── WalletQueryService
└── type
    └── TransferStatus
```

---

## 👥 Módulo de Usuários

### Tipos de usuário

| Tipo | Documento | Permissão |
|---|---|---|
| `COMMON` | CPF (11 dígitos) | Envia e recebe transferências |
| `MERCHANT` | CNPJ (14 dígitos) | Apenas recebe transferências |

### Campos da entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `name` | Nome completo ou razão social. Imutável após cadastro. |
| `email` | E-mail único. Validado por regex. |
| `password` | Hash BCrypt. Nunca armazenada em texto plano. |
| `document` | CPF ou CNPJ. Criptografado com AES-256-CBC. |
| `document_hash` | SHA-256 do documento normalizado. Garante unicidade no banco. |
| `type` | `COMMON` ou `MERCHANT`. Derivado automaticamente do documento. |
| `active` | Indica se o usuário está ativo. |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

### Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/users` | Cria usuário. Tipo derivado do documento. |
| `GET` | `/api/v1/users` | Lista usuários com documento mascarado. |
| `GET` | `/api/v1/users/{id}` | Retorna um usuário. |
| `PATCH` | `/api/v1/users/{id}` | Atualiza e-mail e/ou senha. |
| `DELETE` | `/api/v1/users/{id}` | Remove um usuário. |

### Evento publicado

| Evento | Tópico | Trigger |
|---|---|---|
| `UserCreatedEvent` | `payment.users` | Após persistência do usuário |

---

## 💰 Módulo de Carteira (Wallet)

### Campos da entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `userId` | Referência ao usuário (1:1) |
| `balance` | Saldo em BRL. `BigDecimal`. Nunca negativo. |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

### Regras de negócio

- Saldo nunca pode ser negativo após débito.
- Carteira criada com saldo zero.
- Toda movimentação gera um `WalletDebitedEvent` ou `WalletCreditedEvent`.

### Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/v1/wallets/{userId}` | Retorna saldo e dados da carteira. |

### Eventos consumidos e publicados

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Consome | `UserCreatedEvent` | `payment.users` | Cria a carteira |
| Consome | `TransferCreatedEvent` | `payment.transfers` | Processa transferência |
| Publica | `WalletDebitedEvent` | `payment.wallets` | Após débito |
| Publica | `WalletCreditedEvent` | `payment.wallets` | Após crédito |

---

## 🔄 Módulo de Transferência (Transfer)

Orquestra o fluxo completo de transferência entre usuários. Apenas `COMMON` pode enviar, apenas `MERCHANT` pode receber.

### Campos da entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `sourceWalletId` | Carteira do remetente |
| `destinationWalletId` | Carteira do destinatário |
| `amount` | Valor em BRL. `BigDecimal`. |
| `status` | `PENDING` → `COMPLETED` ou `FAILED` |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

### Fluxo de execução

```
1. POST /api/v1/transfers
   → CreateTransferService.authorize()
   → Cria TransferEntity com status PENDING
   → Publica TransferCreatedEvent (Spring Event)

2. TransferCreatedListener (após commit)
   → TransferPublishService.publish()
   → Publica TransferCreatedEvent no Kafka (retry 3x)

3. TransferWalletConsumer (Kafka)
   → ProcessTransferService.execute()
   → Lock pessimista determinístico (menor UUID primeiro)
   → Débito e crédito atômicos
   → Publica WalletDebitedEvent e WalletCreditedEvent
   → Marca transferência como processada (idempotência)

4. TransferConsumer (Kafka)
   → CreateTransactionService.executeDebit()
   → CreateTransactionService.executeCredit()
   → Publica TransferStatusChangedEvent (COMPLETED)

5. TransferStatusConsumer (Kafka)
   → Atualiza TransferEntity com status final (idempotência)
```

### Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/transfers` | Inicia uma transferência. |
| `GET` | `/api/v1/transfers?walletId=` | Lista transferências da carteira (paginado, ordenado por `createdAt` DESC). |

### Eventos publicados e consumidos

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Publica | `TransferCreatedEvent` | `payment.transfers` | Ao criar transferência (via Kafka) |
| Consome | `TransferStatusChangedEvent` | `payment.transfers` | Atualiza status final |

---

## 📒 Módulo de Autorização (Authorization)

Serviço interno do contexto `transfer`. Valida todas as pré-condições antes de executar uma transferência. Consome apenas contratos de `shared` — sem acoplamento direto com `user` ou `wallet`.

### Regras de autorização

| Regra | Descrição |
|---|---|
| Remetente ativo | `sender.active() == true` |
| Destinatário ativo | `receiver.active() == true` |
| Saldo suficiente | `sourceWallet.balance() >= amount` |
| Tipo do remetente | `sender.canSend() == true` (COMMON) |
| Tipo do destinatário | `receiver.canReceive() == true` (MERCHANT) |
| Transferência para si mesmo | `sender.id() != receiver.id()` |

---

## 📜 Módulo de Transações (Transaction)

Ledger imutável de todas as movimentações de saldo. Alimentado via Kafka — nunca chamado diretamente pelo fluxo de transferência.

### Campos da entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `walletId` | Carteira afetada |
| `transferId` | Referência à transferência origem |
| `type` | `DEBIT` ou `CREDIT` |
| `amount` | Valor em BRL. `BigDecimal`. |
| `createdAt` | Imutável. `setUpdatedAt` sobrescrito para no-op. |

Cada transferência bem-sucedida gera exatamente duas `Transaction`: `DEBIT` no remetente e `CREDIT` no destinatário.

### Eventos consumidos e publicados

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Consome | `WalletDebitedEvent` | `payment.wallets` | Persiste `TransactionEntity` tipo `DEBIT` |
| Consome | `WalletCreditedEvent` | `payment.wallets` | Persiste `TransactionEntity` tipo `CREDIT` |
| Publica | `TransferStatusChangedEvent` | `payment.transfers` | Ao completar ou falhar |

---

## 🐙 Kafka

### Tópicos

| Tópico | DLT | Produzido por | Consumido por |
|---|---|---|---|
| `payment.users` | `payment.users.DLT` | `CreateUserService` | `CreateWalletConsumer` |
| `payment.wallets` | `payment.wallets.DLT` | `ProcessTransferService` | `TransferConsumer` |
| `payment.transfers` | `payment.transfers.DLT` | `TransferPublishService`, `TransferConsumer` | `TransferWalletConsumer`, `TransferStatusConsumer` |

### Configuração de erros

Cada consumer está configurado com `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`:
- 3 tentativas com intervalo de 1 segundo
- Após esgotar tentativas, mensagem enviada ao DLT correspondente

### Retry

- `TransferPublishService`: 3 tentativas com backoff exponencial (1s, 2s, 4s)
- `@Recover`: Publica status FAILED ao esgotar tentativas

---

## 🔒 Segurança e Consistência

### Lock Pessimista Determinístico

O `ProcessTransferService` implementa um algoritmo de lock determinístico para evitar deadlocks:

```java
// Sempre lock a carteira com menor UUID primeiro
UUID firstWalletId = sourceWalletId.compareTo(destinationWalletId) <= 0
    ? sourceWalletId
    : destinationWalletId;
```

**Benefícios:**
- Garante ordem total de locks entre todas as transferências
- Previne circular wait matematicamente
- Permite concorrência em transferências não conflitantes

### Idempotência

| Contexto | Implementação |
|---|---|
| `wallet` | `ProcessedTransferRepository` - marca transferência como processada |
| `transfer` | Verifica status antes de atualizar `TransferEntity` |
| `transaction` | (Future) Verifica se já existe `TransactionEntity` com mesmo `transferId` e `type` |

---

## 🚀 Como Executar

### Pré-requisitos

- Java 21
- Maven 3.8+
- Docker e Docker Compose

### Setup Local

```bash
# Clone o repositório
git clone <repository-url>
cd payment-service

# Configure a secret de criptografia
export APP_CRYPTO_SECRET="your-secret-key-32-characters"

# Inicie os serviços (PostgreSQL, Kafka, Payment Service)
docker-compose up -d

# Verifique os logs
docker-compose logs -f payment-service

# A aplicação estará disponível em http://localhost:8080
```

### Executar Testes

```bash
# Executar todos os testes
mvn test

# Executar com cobertura
mvn test jacoco:report
```

### Build e Deploy

```bash
# Build JAR
mvn clean package

# Build Docker image
docker build -t payment-service:latest .

# Push para registry
docker tag payment-service:latest <registry>/payment-service:latest
docker push <registry>/payment-service:latest
```

---

## 📊 Monitoramento

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Metrics

```bash
curl http://localhost:8080/actuator/metrics
```

### Kafka Topics

```bash
# Listar tópicos
docker exec -it payment-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Consumir mensagens de um tópico
docker exec -it payment-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic payment.transfers --from-beginning
```

---

## 🎯 Decisões de Design

| Decisão | Justificativa |
|---|---|
| `UserType` derivado do documento | Elimina inconsistência entre tipo e documento. `CPF → COMMON`, `CNPJ → MERCHANT`. Imutável após cadastro. |
| `BigDecimal` para valores monetários | Evita erros de arredondamento em operações financeiras. |
| AES-256-CBC com IV aleatório | Mesmo CPF/CNPJ gera ciphertexts diferentes a cada persistência. |
| Hash SHA-256 para unicidade | Constraint `UNIQUE` no banco via hash determinístico, independente do ciphertext. |
| `Transaction` imutável | Auditabilidade completa. `setUpdatedAt` sobrescrito como no-op. |
| Wallet criada via evento | Desacopla `User` e `Wallet`. `CreateWalletConsumer` reage ao `UserCreatedEvent`. |
| `shared.query` com interfaces | `AuthorizationService` depende de interfaces, não de implementações de outros contextos. |
| `UserSummary` e `WalletSummary` em `shared` | Contratos de leitura transversais sem vazar entidades entre contextos. |
| `TransferStatus` em `shared.type` | Usado em eventos e em múltiplos contextos — sem dono único. |
| Lock pessimista determinístico | Previne deadlocks ao garantir ordem total de locks entre carteiras. |
| Spring Events + Kafka | Spring Events para comunicação síncrona intra-contexto, Kafka para comunicação assíncrona inter-contexto. |
| Retry com backoff | 3 tentativas com delay progressivo (1s, 2s, 4s) para lidar com falhas transitórias. |
| DLT por tópico | Mensagens com falha são isoladas sem bloquear o consumo do tópico principal. |

---

## 📝 API Examples

### Criar Usuário

```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "João Silva",
    "email": "joao@example.com",
    "password": "SecurePass123!",
    "document": "12345678901"
  }'
```

### Criar Transferência

```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceWalletId": "<source-wallet-uuid>",
    "destinationWalletId": "<dest-wallet-uuid>",
    "amount": 100.50
  }'
```

### Listar Transferências

```bash
curl "http://localhost:8080/api/v1/transfers?walletId=<wallet-uuid>&page=0&size=20"
```

### Consultar Saldo

```bash
curl http://localhost:8080/api/v1/wallets/<user-uuid>
```

---

## 📄 Licença

Este projeto está sob licença [MIT](LICENSE).
