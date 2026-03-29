# payment-service

API REST para gerenciamento de pagamentos com arquitetura baseada em eventos · Java 21 · Spring Boot 3 · JWT · Outbox Pattern

---

## 📋 Visão Geral

O **payment-service** é uma API REST que gerencia usuários, carteiras digitais e transferências financeiras entre pessoas físicas (COMMON) e lojistas (MERCHANT), seguindo os princípios de **Domain-Driven Design (DDD)** e **Event-Driven Architecture (EDA)**.

A aplicação utiliza **Kafka** para comunicação assíncrona entre contextos de domínio, garantindo desacoplamento e escalabilidade. O padrão **Outbox** garante consistência entre o banco de dados e a publicação de eventos. O fluxo de transferência implementa **lock pessimista determinístico**, **idempotência** e **retry com backoff** para garantir consistência em operações financeiras críticas.

| | |
|---|---|
| **Linguagem** | Java 21 |
| **Framework** | Spring Boot 3.5 |
| **Banco de dados** | PostgreSQL 16 |
| **Mensageria** | Apache Kafka 7.5 |
| **Cache/Estado** | Redis 7 |
| **Segurança** | AES-256-CBC + BCrypt + JWT |
| **Validação** | Bean Validation + Value Objects |
| **Build** | Maven |
| **Migrações** | Flyway |
| **Monitoramento** | Prometheus + Grafana |
| **Payment Providers** | Stripe (extensível) |

---

## 🏗️ Arquitetura

### Contextos de Domínio

```
payment_service
├── shared          ← contratos, eventos, infraestrutura transversal
├── user            ← cadastro, autenticação e gestão de usuários
├── wallet          ← carteiras digitais, transferências e depósitos
├── transfer        ← orquestração de transferências
├── transaction     ← ledger imutável de movimentações
├── deposit         ← processamento de depósitos e webhooks
└── auth            ← autenticação JWT e autorização
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
│   ├── KafkaEventProducer
│   └── OutboxPublisher
├── outbox
│   ├── OutboxEvent
│   └── OutboxRepository
├── query
│   ├── UserQueryService
│   └── WalletQueryService
├── security
│   ├── JwtService
│   └── SecurityUtils
└── type
    └── TransferStatus
```

---

## 🔐 Módulo de Autenticação (Auth)

Autenticação stateless baseada em JWT com invalidação de tokens via Redis.

### Fluxo de Autenticação

| Endpoint | Acesso | Descrição |
|---|---|---|
| `POST /api/v1/auth/login` | Público | Login com `identifier` (email ou CPF/CNPJ) e `password`. Retorna JWT e tempo de expiração. |
| `POST /api/v1/auth/logout` | Autenticado | Revoga o token atual armazenando seu ID no Redis até a expiração natural. |

### Características de Segurança

| Recurso | Implementação |
|---|---|
| Token JWT | JJWT com chave HMAC Base64 |
| Revogação | Redis com TTL igual ao tempo restante do token |
| Rate Limiting | Bucket4j + Redis (por IP para endpoints públicos, por usuário para autenticados) |
| Proteção de endpoints | Spring Security com anotações `@PreAuthorize` |
| Verificação de propriedade | `SecurityUtils.requireOwnership()` em controllers |

---

## 👥 Módulo de Usuários

### Tipos de usuário

| Tipo | Documento | Permissão |
|---|---|---|
| `COMMON` | CPF (11 dígitos) | Envia e recebe transferências |
| `MERCHANT` | CNPJ (14 dígitos) | Apenas recebe transferências |
| `ADMIN` | - | Acesso total (não exposto em endpoints públicos) |

### Campos da entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `name` | Nome completo ou razão social. Imutável após cadastro. |
| `email` | E-mail único. Validado por regex. Criptografado via JPA converter. |
| `password` | Hash BCrypt. Nunca armazenada em texto plano. |
| `document` | CPF ou CNPJ. Criptografado com AES-256-CBC. |
| `document_hash` | SHA-256 do documento normalizado. Garante unicidade no banco. |
| `type` | `COMMON` ou `MERCHANT`. Derivado automaticamente do documento. |
| `active` | Indica se o usuário está ativo. |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

### Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/api/v1/users` | Público | Cria usuário. Tipo derivado do documento. |
| `GET` | `/api/v1/users` | `ADMIN` | Lista usuários com paginação. |
| `GET` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Retorna um usuário. |
| `PATCH` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Atualiza e-mail e/ou senha. |
| `DELETE` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Remove um usuário. |

### Evento publicado

| Evento | Tópico | Trigger |
|---|---|---|
| `UserCreatedEvent` | `payment.users` | Após persistência do usuário (via Outbox) |

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
- Toda movimentação gera um `WalletDebitedEvent` ou `WalletCreditedEvent` (via Outbox).

### Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/api/v1/wallets/{userId}` | Owner ou `ADMIN` | Retorna saldo e dados da carteira. |

### Eventos consumidos e publicados

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Consome | `UserCreatedEvent` | `payment.users` | Cria a carteira |
| Consome | `TransferCreatedEvent` | `payment.transfer.created` | Processa transferência |
| Publica | `WalletDebitedEvent` | `payment.wallet.debits` | Após débito (via Outbox) |
| Publica | `WalletCreditedEvent` | `payment.wallet.credits` | Após crédito (via Outbox) |

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
   → Escreve TRANSFER_CREATED na tabela outbox

2. OutboxPublisher (polling a cada 500ms)
   → Publica TransferCreatedEvent no Kafka (retry 3x)
   → Marca outbox como processado ou incrementa tentativas

3. TransferWalletConsumer (Kafka)
   → ProcessTransferService.execute()
   → Lock pessimista determinístico (menor UUID primeiro)
   → Débito e crédito atômicos
   → Escreve WALLET_DEBITED e WALLET_CREDITED na outbox
   → Marca transferência como processada (idempotência)

4. OutboxPublisher
   → Publica WalletDebitedEvent e WalletCreditedEvent

5. transaction.TransferConsumer (Kafka)
   → CreateTransactionService.executeDebit()
   → CreateTransactionService.executeCredit()
   → Escreve TRANSFER_STATUS_CHANGED na outbox (COMPLETED)

6. OutboxPublisher
   → Publica TransferStatusChangedEvent

7. TransferStatusConsumer (Kafka)
   → Atualiza TransferEntity com status final (idempotência)
```

### Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/api/v1/transfers` | `COMMON` ou `ADMIN` | Inicia uma transferência. Verifica propriedade da carteira origem. |
| `GET` | `/api/v1/transfers?walletId=` | Owner ou `ADMIN` | Lista transferências da carteira (paginado, ordenado por `createdAt` DESC). |

### Eventos publicados e consumidos

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Publica | `TransferCreatedEvent` | `payment.transfer.created` | Ao criar transferência (via Outbox) |
| Consome | `TransferStatusChangedEvent` | `payment.transfer.status` | Atualiza status final |

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

## 💵 Módulo de Depósitos (Deposit)

Gerencia o processo de depósito em carteiras digitais através de integração com provedores de pagamento (Stripe).

### Campos da entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `walletId` | Carteira que receberá o depósito |
| `amount` | Valor em BRL. `BigDecimal`. |
| `status` | `PENDING` → `COMPLETED` ou `FAILED` |
| `paymentProvider` | `STRIPE` (extensível para outros provedores) |
| `externalPaymentId` | ID da transação no provedor de pagamento |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

### Fluxo de execução

```
1. POST /api/v1/deposits
   → CreateDepositService.create()
   → Cria DepositEntity com status PENDING
   → Integra com provedor de pagamento (Stripe)
   → Retorna checkout URL para o usuário

2. Webhook do provedor de pagamento
   → DepositController.webhook()
   → Verifica assinatura do webhook
   → ProcessDepositService.process()
   → Atualiza saldo da carteira
   → Marca depósito como COMPLETED
   → Publica DepositCompletedEvent no Kafka

3. TransactionConsumer (Kafka)
   → Persiste TransactionEntity tipo CREDIT
```

### Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/api/v1/deposits` | Autenticado | Cria um depósito e retorna URL de checkout do provedor de pagamento |
| `POST` | `/api/v1/deposits/webhook` | Público | Recebe webhooks de provedores de pagamento (assinatura verificada) |
| `GET` | `/api/v1/deposits/{id}` | Owner ou `ADMIN` | Retorna detalhes do depósito |
| `GET` | `/api/v1/deposits?walletId=` | Owner ou `ADMIN` | Lista depósitos da carteira (paginado, ordenado por `createdAt` DESC) |

### Eventos publicados e consumidos

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Publica | `DepositCompletedEvent` | `payment.deposit.completed` | Ao completar depósito (via Outbox) |

### Provedores de pagamento suportados

| Provedor | Status |
|---|---|
| `STRIPE` | ✅ Implementado |
| Outros | 🔌 Extensível |

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
| Consome | `WalletDebitedEvent` | `payment.wallet.debits` | Persiste `TransactionEntity` tipo `DEBIT` |
| Consome | `WalletCreditedEvent` | `payment.wallet.credits` | Persiste `TransactionEntity` tipo `CREDIT` |
| Publica | `TransferStatusChangedEvent` | `payment.transfer.status` | Ao completar ou falhar (via Outbox) |

---

## 📦 Outbox Pattern

O serviço utiliza uma tabela `outbox` para garantir consistência entre o banco de dados transacional e a publicação de eventos Kafka.

### Comportamento

| Aspecto | Configuração |
|---|---|
| Polling interval | 500 ms (padrão) |
| Retry máximo | 10 tentativas |
| Cleanup | Linhas processadas removidas periodicamente |
| Recovery | Transferência marcada como `FAILED` se `TRANSFER_CREATED` não puder ser publicado |

### Fluxo

1. Serviço de negócio persiste dados de negócio + insere evento na `outbox` (mesma transação)
2. `OutboxPublisher` consome eventos pendentes e publica no Kafka
3. Em caso de sucesso, marca outbox como processado
4. Em caso de falha, incrementa tentativas; após 10 falhas, aplica lógica de recovery

---

## 🐙 Kafka

### Tópicos

| Tópico | DLT | Produzido por | Consumido por |
|---|---|---|---|
| `payment.users` | `payment.users.DLT` | `CreateUserService` (via Outbox) | `CreateWalletConsumer` |
| `payment.wallet.debits` | `payment.wallet.debits.DLT` | `ProcessTransferService` (via Outbox) | `TransferConsumer` (transaction) |
| `payment.wallet.credits` | `payment.wallet.credits.DLT` | `ProcessTransferService` (via Outbox) | `TransferConsumer` (transaction) |
| `payment.transfer.created` | `payment.transfer.created.DLT` | `OutboxPublisher` | `TransferWalletConsumer` |
| `payment.transfer.status` | `payment.transfer.status.DLT` | `TransferConsumer` (via Outbox) | `TransferStatusConsumer` |

### Configuração de erros

Cada consumer está configurado com `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`:
- 3 tentativas com intervalo fixo de 1 segundo
- Após esgotar tentativas, mensagem enviada ao DLT correspondente

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
| `transaction` | Verifica se já existe `TransactionEntity` com mesmo `transferId` e `type` |

### Rate Limiting

| Endpoint Tipo | Estratégia |
|---|---|
| Público (`/api/v1/auth/login`, `/api/v1/users`) | Por IP via Bucket4j + Redis |
| Autenticado | Por usuário autenticado |

---

## 🚀 Como Executar

### Pré-requisitos

- Java 21
- Maven 3.8+
- Docker e Docker Compose

### Variáveis de Ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `APP_CRYPTO_SECRET` | Sim | Chave de 32 caracteres para `AesEncryptor` |
| `JWT_SECRET` | Sim | Chave HMAC Base64 para JJWT |
| `SPRING_REDIS_HOST` | Não | Padrão: `localhost` |
| `SPRING_REDIS_PORT` | Não | Padrão: `6379` |
| `RATE_LIMIT_ENABLED` | Não | Padrão: `true` |

### Setup Local

```bash
# Clone o repositório
git clone <repository-url>
cd payment-service

# Inicie apenas a infraestrutura
docker compose up -d postgres zookeeper kafka redis

# Configure as secrets e execute
export APP_CRYPTO_SECRET="your-secret-key-32-characters"
export JWT_SECRET="<base64-encoded-hmac-key>"
./mvnw spring-boot:run
```

### Executar com Docker Compose (Full Stack)

> **Nota:** O `docker-compose.yml` já repassa `JWT_SECRET` para o container. Garanta apenas que esse valor exista no seu `.env`.

```bash
# Inicia todos os serviços incluindo monitoramento
docker compose up --build
```

A aplicação estará disponível em:
- **API**: http://localhost:8080
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090

### Scripts utilitários

```bash
# Popular banco de dados com dados de teste
./seed.sh

# Executar testes de carga
./load.sh
```

### Executar Testes

```bash
# Unit tests
./mvnw test

# Integration tests com Testcontainers
./mvnw verify

# Com cobertura
./mvnw test jacoco:report
```

---

## 📊 Monitoramento

### Stack de Observabilidade

A aplicação utiliza uma stack moderna de observabilidade composta por:

| Componente | Descrição |
|---|---|
| **Prometheus** | Coleta e armazena métricas da aplicação |
| **Grafana** | Dashboards visuais para monitoramento em tempo real |
| **Logback** | Logging estruturado em JSON para melhor ingestão |
| **Micrometer** | Abstração para métricas da aplicação |

### Métricas disponíveis

| Categoria | Métricas |
|---|---|
| **Transferências** | Contagem, taxa de sucesso/falha, tempo de execução |
| **Depósitos** | Contagem, por provedor, tempo de processamento |
| **Carteiras** | Saldos, transações por carteira |
| **Kafka** | Mensagens produzidas/consumidas, latência |
| **HTTP** | Requisições por endpoint, tempo de resposta, status codes |
| **JVM** | Heap, GC, threads |

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
docker exec -it payment-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment.transfer.created \
  --from-beginning
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
| Outbox Pattern | Garante consistência entre persistência transacional e publicação Kafka. |
| JWT + Redis | Autenticação stateless com capacidade de revogação imediata. |
| Rate Limiting distribuído | Bucket4j + Redis protege contra abuse em ambiente multi-instância. |
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

### Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "identifier": "joao@example.com",
    "password": "SecurePass123!"
  }'
```

### Consultar Carteira

```bash
curl http://localhost:8080/api/v1/wallets/<user-uuid> \
  -H "Authorization: Bearer <jwt>"
```

### Criar Transferência

```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceWalletId": "<source-wallet-uuid>",
    "destinationWalletId": "<dest-wallet-uuid>",
    "amount": 100.50
  }'
```

### Listar Transferências

```bash
curl "http://localhost:8080/api/v1/transfers?walletId=<wallet-uuid>&page=0&size=20" \
  -H "Authorization: Bearer <jwt>"
```

### Criar Depósito

```bash
curl -X POST http://localhost:8080/api/v1/deposits \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "walletId": "<wallet-uuid>",
    "amount": 500.00,
    "paymentProvider": "STRIPE"
  }'
```

### Listar Depósitos

```bash
curl "http://localhost:8080/api/v1/deposits?walletId=<wallet-uuid>&page=0&size=20" \
  -H "Authorization: Bearer <jwt>"
```

### Logout

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <jwt>"
```

---

## 📄 Licença

Este projeto está sob licença [MIT](LICENSE).
