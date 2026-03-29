# payment-service

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Apache_Kafka-7.5-black?logo=apachekafka)](https://kafka.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> API REST para gerenciamento de pagamentos com arquitetura baseada em eventos · Java 21 · Spring Boot 3 · JWT · Outbox Pattern

---

## 📑 Índice

- [Visão Geral](#-visão-geral)
- [Arquitetura](#️-arquitetura)
- [Módulos](#-módulos)
  - [Auth](#-módulo-de-autenticação-auth)
  - [User](#-módulo-de-usuários)
  - [Wallet](#-módulo-de-carteira-wallet)
  - [Transfer](#-módulo-de-transferência-transfer)
  - [Autorização](#-módulo-de-autorização-authorization)
  - [Deposit](#-módulo-de-depósitos-deposit)
  - [Transaction](#-módulo-de-transações-transaction)
- [Outbox Pattern](#-outbox-pattern)
- [Kafka](#-kafka)
- [Segurança e Consistência](#-segurança-e-consistência)
- [Monitoramento](#-monitoramento)
- [Como Executar](#-como-executar)
- [Decisões de Design](#-decisões-de-design)
- [Contribuindo](#-contribuindo)
- [Licença](#-licença)

---

## 📋 Visão Geral

O **payment-service** é uma API REST que gerencia usuários, carteiras digitais e transferências financeiras entre pessoas físicas (`COMMON`) e lojistas (`MERCHANT`), seguindo os princípios de **Domain-Driven Design (DDD)** e **Event-Driven Architecture (EDA)**.

A aplicação utiliza **Kafka** para comunicação assíncrona entre contextos de domínio, garantindo desacoplamento e escalabilidade. O padrão **Outbox** garante consistência entre o banco de dados e a publicação de eventos. O fluxo de transferência implementa **lock pessimista determinístico**, **idempotência** e **retry com backoff** para garantir consistência em operações financeiras críticas.

### Stack

| | |
|---|---|
| **Linguagem** | Java 21 |
| **Framework** | Spring Boot 3.5 |
| **Banco de dados** | PostgreSQL 16 |
| **Mensageria** | Apache Kafka 7.5 |
| **Cache / Estado** | Redis 7 |
| **Segurança** | AES-256-CBC · BCrypt · JWT (JJWT) |
| **Rate Limiting** | Bucket4j + Redis |
| **Build** | Maven |
| **Migrações** | Flyway |
| **Monitoramento** | Prometheus + Grafana + Micrometer |
| **Payment Providers** | Stripe (extensível) |
| **Testes** | JUnit 5 + Testcontainers |

---

## 🏗️ Arquitetura

### Contextos de Domínio

```
payment_service
├── shared          ← contratos, eventos e infraestrutura transversal
├── auth            ← autenticação JWT e autorização
├── user            ← cadastro e gestão de usuários
├── wallet          ← carteiras digitais, débito e crédito
├── transfer        ← orquestração de transferências
├── authorization   ← validação de pré-condições de transferência
├── transaction     ← ledger imutável de movimentações
└── deposit         ← processamento de depósitos via provedores externos
```

### Módulo `shared`

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

### Fluxo de Eventos (visão macro)

```
[POST /transfers]
      │
      ▼
CreateTransferService ──(Outbox)──► Kafka: payment.transfer.created
                                          │
                                          ▼
                               TransferWalletConsumer
                               (ProcessTransferService)
                               lock determinístico
                               débito + crédito atômicos
                                    │
                          (Outbox)  ├──► Kafka: payment.wallet.debits
                                    └──► Kafka: payment.wallet.credits
                                               │
                                               ▼
                                    TransferConsumer (transaction)
                                    persiste DEBIT + CREDIT no ledger
                                          │
                                    (Outbox)──► Kafka: payment.transfer.status
                                                      │
                                                      ▼
                                           TransferStatusConsumer
                                           atualiza TransferEntity (COMPLETED)
```

---

## 📦 Módulos

---

### 🔐 Módulo de Autenticação (Auth)

Autenticação stateless baseada em JWT com invalidação de tokens via Redis.

#### Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Público | Login com `identifier` (email ou CPF/CNPJ) e `password`. Retorna JWT e tempo de expiração. |
| `POST` | `/api/v1/auth/logout` | Autenticado | Revoga o token atual, armazenando seu JTI no Redis até a expiração natural. |

#### Características de Segurança

| Recurso | Implementação |
|---|---|
| Token JWT | JJWT com chave HMAC Base64 |
| Revogação | Redis com TTL igual ao tempo restante do token |
| Rate Limiting | Bucket4j + Redis (por IP para endpoints públicos; por usuário para autenticados) |
| Proteção de endpoints | Spring Security com `@PreAuthorize` |
| Verificação de propriedade | `SecurityUtils.requireOwnership()` nos controllers |

---

### 👥 Módulo de Usuários

#### Tipos de Usuário

| Tipo | Documento | Permissão |
|---|---|---|
| `COMMON` | CPF (11 dígitos) | Envia e recebe transferências |
| `MERCHANT` | CNPJ (14 dígitos) | Apenas recebe transferências |
| `ADMIN` | — | Acesso total (não exposto em endpoints públicos) |

> O `UserType` é derivado automaticamente do documento (`CPF → COMMON`, `CNPJ → MERCHANT`) e é imutável após o cadastro.

#### Campos da Entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `name` | Nome completo ou razão social. Imutável após cadastro. |
| `email` | Único. Validado por regex. Criptografado via JPA Converter. |
| `password` | Hash BCrypt. Nunca armazenada em texto plano. |
| `document` | CPF ou CNPJ. Criptografado com AES-256-CBC + IV aleatório. |
| `document_hash` | SHA-256 do documento normalizado. Garante unicidade no banco. |
| `type` | `COMMON` ou `MERCHANT`. Derivado do documento. |
| `active` | Indica se o usuário está ativo. |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

#### Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/api/v1/users` | Público | Cria usuário. Tipo derivado do documento. |
| `GET` | `/api/v1/users` | `ADMIN` | Lista usuários com paginação. |
| `GET` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Retorna um usuário. |
| `PATCH` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Atualiza e-mail e/ou senha. |
| `DELETE` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Remove um usuário. |

#### Evento Publicado

| Evento | Tópico | Trigger |
|---|---|---|
| `UserCreatedEvent` | `payment.users` | Após persistência do usuário (via Outbox) |

---

### 💰 Módulo de Carteira (Wallet)

#### Campos da Entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `userId` | Referência ao usuário (relação 1:1) |
| `balance` | Saldo em BRL. `BigDecimal`. Nunca negativo. |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

#### Regras de Negócio

- Carteira criada com saldo zero, reativamente ao `UserCreatedEvent`.
- Saldo nunca pode ser negativo após débito.
- Toda movimentação gera um evento via Outbox.

#### Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/api/v1/wallets/{userId}` | Owner ou `ADMIN` | Retorna saldo e dados da carteira. |

#### Eventos

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Consome | `UserCreatedEvent` | `payment.users` | Cria a carteira |
| Consome | `TransferCreatedEvent` | `payment.transfer.created` | Processa débito e crédito |
| Publica | `WalletDebitedEvent` | `payment.wallet.debits` | Após débito (via Outbox) |
| Publica | `WalletCreditedEvent` | `payment.wallet.credits` | Após crédito (via Outbox) |

---

### 🔄 Módulo de Transferência (Transfer)

Orquestra o fluxo completo de transferência entre usuários. Apenas `COMMON` pode enviar; qualquer usuário ativo pode receber.

#### Campos da Entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `sourceWalletId` | Carteira do remetente |
| `destinationWalletId` | Carteira do destinatário |
| `amount` | Valor em BRL. `BigDecimal`. |
| `status` | `PENDING` → `COMPLETED` ou `FAILED` |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

#### Fluxo de Execução

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

#### Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/api/v1/transfers` | `COMMON` ou `ADMIN` | Inicia uma transferência. Verifica propriedade da carteira de origem. |
| `GET` | `/api/v1/transfers?walletId=` | Owner ou `ADMIN` | Lista transferências da carteira (paginado, ordenado por `createdAt` DESC). |

#### Eventos

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Publica | `TransferCreatedEvent` | `payment.transfer.created` | Ao criar transferência (via Outbox) |
| Consome | `TransferStatusChangedEvent` | `payment.transfer.status` | Atualiza status final |

---

### 🛡️ Módulo de Autorização (Authorization)

Serviço interno do contexto `transfer`. Valida todas as pré-condições antes de executar uma transferência. Consome apenas contratos de `shared` — sem acoplamento direto com `user` ou `wallet`.

#### Regras de Autorização

| Regra | Condição |
|---|---|
| Remetente ativo | `sender.active() == true` |
| Destinatário ativo | `receiver.active() == true` |
| Saldo suficiente | `sourceWallet.balance() >= amount` |
| Tipo do remetente | `sender.canSend() == true` (apenas `COMMON`) |
| Tipo do destinatário | `receiver.canReceive() == true` (apenas `MERCHANT`) |
| Auto-transferência | `sender.id() != receiver.id()` |

---

### 💵 Módulo de Depósitos (Deposit)

Gerencia depósitos em carteiras digitais via integração com provedores de pagamento (Stripe).

#### Campos da Entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `walletId` | Carteira que receberá o depósito |
| `amount` | Valor em BRL. `BigDecimal`. |
| `status` | `PENDING` → `COMPLETED` ou `FAILED` |
| `paymentProvider` | `STRIPE` (extensível) |
| `externalPaymentId` | ID da transação no provedor externo |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

#### Fluxo de Execução

```
1. POST /api/v1/deposits
   → CreateDepositService.create()
   → Cria DepositEntity com status PENDING
   → Integra com provedor de pagamento (Stripe)
   → Retorna checkout URL para o usuário

2. Webhook do provedor de pagamento
   → DepositController.webhook()
   → Verifica assinatura do webhook (STRIPE_WEBHOOK_SECRET)
   → ProcessDepositService.process()
   → Atualiza saldo da carteira
   → Marca depósito como COMPLETED
   → Publica DepositCompletedEvent via Outbox

3. TransactionConsumer (Kafka)
   → Persiste TransactionEntity tipo CREDIT
```

#### Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/api/v1/deposits` | Autenticado | Cria um depósito e retorna URL de checkout. |
| `POST` | `/api/v1/deposits/webhook` | Público (assinatura verificada) | Recebe webhooks dos provedores de pagamento. |
| `GET` | `/api/v1/deposits/{id}` | Owner ou `ADMIN` | Retorna detalhes de um depósito. |
| `GET` | `/api/v1/deposits?walletId=` | Owner ou `ADMIN` | Lista depósitos da carteira (paginado, ordenado por `createdAt` DESC). |

#### Eventos

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Publica | `DepositCompletedEvent` | `payment.deposit.completed` | Ao completar depósito (via Outbox) |

#### Provedores Suportados

| Provedor | Status |
|---|---|
| `STRIPE` | ✅ Implementado |
| Outros | 🔌 Extensível via interface `PaymentProvider` |

---

### 📒 Módulo de Transações (Transaction)

Ledger imutável de todas as movimentações de saldo. Alimentado exclusivamente via Kafka — nunca chamado diretamente por outros contextos.

#### Campos da Entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `walletId` | Carteira afetada |
| `transferId` | Referência à transferência de origem |
| `type` | `DEBIT` ou `CREDIT` |
| `amount` | Valor em BRL. `BigDecimal`. |
| `createdAt` | Imutável. `setUpdatedAt` sobrescrito como no-op. |

> Cada transferência bem-sucedida gera exatamente duas `Transaction`: um `DEBIT` no remetente e um `CREDIT` no destinatário.

#### Eventos

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Consome | `WalletDebitedEvent` | `payment.wallet.debits` | Persiste `TransactionEntity` tipo `DEBIT` |
| Consome | `WalletCreditedEvent` | `payment.wallet.credits` | Persiste `TransactionEntity` tipo `CREDIT` |
| Consome | `DepositCompletedEvent` | `payment.deposit.completed` | Persiste `TransactionEntity` tipo `CREDIT` |
| Publica | `TransferStatusChangedEvent` | `payment.transfer.status` | Ao completar ou falhar (via Outbox) |

---

## 📦 Outbox Pattern

A tabela `outbox` garante consistência entre a persistência transacional e a publicação de eventos no Kafka — nunca há publicação sem persistência, nem persistência sem publicação eventual.

```
Serviço de negócio
  ├── persiste entidade de domínio   ┐
  └── insere evento na outbox        ┘ (mesma transação @Transactional)
         │
         ▼
   OutboxPublisher (polling a cada 500ms)
         ├── sucesso → marca como processado
         └── falha   → incrementa tentativas
                           └── após 10 falhas → recovery (FAILED)
```

| Aspecto | Configuração |
|---|---|
| Polling interval | 500 ms |
| Retry máximo | 10 tentativas |
| Cleanup | Registros processados removidos periodicamente |
| Recovery | Transferência marcada como `FAILED` se `TRANSFER_CREATED` esgotar tentativas |

---

## 🐙 Kafka

### Mapa de Tópicos

| Tópico | DLT | Produzido por | Consumido por |
|---|---|---|---|
| `payment.users` | `payment.users.DLT` | `CreateUserService` | `CreateWalletConsumer` |
| `payment.wallet.debits` | `payment.wallet.debits.DLT` | `ProcessTransferService` | `TransferConsumer` (transaction) |
| `payment.wallet.credits` | `payment.wallet.credits.DLT` | `ProcessTransferService` | `TransferConsumer` (transaction) |
| `payment.transfer.created` | `payment.transfer.created.DLT` | `OutboxPublisher` | `TransferWalletConsumer` |
| `payment.transfer.status` | `payment.transfer.status.DLT` | `TransferConsumer` (transaction) | `TransferStatusConsumer` |
| `payment.deposit.completed` | `payment.deposit.completed.DLT` | `ProcessDepositService` | `TransactionConsumer` |

### Tratamento de Erros

Cada consumer está configurado com `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`:
- **3 tentativas** com intervalo fixo de 1 segundo
- Após esgotar as tentativas, a mensagem é enviada ao DLT correspondente

---

## 🔒 Segurança e Consistência

### Lock Pessimista Determinístico

O `ProcessTransferService` ordena os locks por UUID para eliminar a possibilidade de deadlock circular:

```java
// Sempre adquire o lock da carteira com menor UUID primeiro
UUID firstWalletId = sourceWalletId.compareTo(destinationWalletId) <= 0
    ? sourceWalletId
    : destinationWalletId;
```

**Garantias:**
- Ordem total de aquisição de locks entre todas as transferências concorrentes
- Elimina matematicamente o *circular wait*
- Permite concorrência plena entre transferências não conflitantes

### Idempotência

| Contexto | Mecanismo |
|---|---|
| `wallet` | `ProcessedTransferRepository` — registra transferências já processadas |
| `transfer` | Verifica status atual antes de atualizar `TransferEntity` |
| `transaction` | Verifica existência de `TransactionEntity` com mesmo `transferId` e `type` |

### Rate Limiting

| Tipo de Endpoint | Estratégia |
|---|---|
| Público (`/auth/login`, `/users`) | Por IP via Bucket4j + Redis |
| Autenticado | Por usuário autenticado via Bucket4j + Redis |

---

## 📊 Monitoramento

### Stack de Observabilidade

| Componente | Função |
|---|---|
| **Prometheus** | Coleta e armazena métricas da aplicação |
| **Grafana** | Dashboards em tempo real |
| **Micrometer** | Abstração de métricas no código Java |
| **Logback** | Logging estruturado em JSON |

### Métricas Disponíveis

| Categoria | Métricas |
|---|---|
| **Transferências** | Contagem, taxa de sucesso/falha, tempo de execução |
| **Depósitos** | Contagem por provedor, tempo de processamento |
| **Carteiras** | Saldos, movimentações por carteira |
| **Kafka** | Mensagens produzidas/consumidas, latência |
| **HTTP** | Requisições por endpoint, tempo de resposta, status codes |
| **JVM** | Heap, GC, threads |

### Endpoints de Saúde

```bash
# Health check
curl http://localhost:8080/actuator/health

# Todas as métricas disponíveis
curl http://localhost:8080/actuator/metrics
```

---

## 🚀 Como Executar

### Pré-requisitos

- Java 21
- Maven 3.8+
- Docker e Docker Compose
- Conta no Stripe (para o módulo de depósitos)

### Variáveis de Ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `APP_CRYPTO_SECRET` | ✅ | Chave de exatamente 32 caracteres para `AesEncryptor` |
| `JWT_SECRET` | ✅ | Chave HMAC codificada em Base64 para JJWT |
| `STRIPE_SECRET_KEY` | ✅ | Chave secreta da API do Stripe (`sk_...`) |
| `STRIPE_WEBHOOK_SECRET` | ✅ | Segredo para validação de webhooks do Stripe (`whsec_...`) |
| `SPRING_REDIS_HOST` | ❌ | Padrão: `localhost` |
| `SPRING_REDIS_PORT` | ❌ | Padrão: `6379` |
| `RATE_LIMIT_ENABLED` | ❌ | Padrão: `true` |

### Setup Local (somente infraestrutura)

```bash
# Clone o repositório
git clone <repository-url>
cd payment-service

# Suba apenas a infraestrutura
docker compose up -d postgres zookeeper kafka redis

# Configure as variáveis e execute
export APP_CRYPTO_SECRET="your-secret-key-32-characters"
export JWT_SECRET="<base64-encoded-hmac-key>"
export STRIPE_SECRET_KEY="sk_test_..."
export STRIPE_WEBHOOK_SECRET="whsec_..."

./mvnw spring-boot:run
```

### Full Stack com Docker Compose

```bash
# Copia o template e preenche as variáveis
cp .env.example .env

# Sobe todos os serviços, incluindo monitoramento
docker compose up --build
```

Serviços disponíveis após o boot:

| Serviço | URL |
|---|---|
| API | http://localhost:8080 |
| Grafana | http://localhost:3000 (admin/admin) |
| Prometheus | http://localhost:9090 |

### Scripts Utilitários

```bash
# Popula o banco com dados de teste
./seed.sh

# Executa testes de carga
./load.sh
```

### Executar Testes

```bash
# Testes unitários
./mvnw test

# Testes de integração com Testcontainers
./mvnw verify

# Com relatório de cobertura (Jacoco)
./mvnw test jacoco:report
```

### Inspecionar Tópicos Kafka

```bash
# Listar todos os tópicos
docker exec -it payment-kafka kafka-topics \
  --bootstrap-server localhost:9092 --list

# Consumir mensagens de um tópico
docker exec -it payment-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment.transfer.created \
  --from-beginning
```

---

## 📝 Exemplos de API

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

### Logout

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <jwt>"
```

---

## 🎯 Decisões de Design

| Decisão | Justificativa |
|---|---|
| `UserType` derivado do documento | Elimina inconsistência entre tipo e documento. `CPF → COMMON`, `CNPJ → MERCHANT`. Imutável após cadastro. |
| `BigDecimal` para valores monetários | Evita erros de arredondamento em operações financeiras. |
| AES-256-CBC com IV aleatório | Mesmo CPF/CNPJ gera ciphertexts diferentes a cada persistência, prevenindo análise estatística. |
| Hash SHA-256 para unicidade | Constraint `UNIQUE` no banco via hash determinístico, independente do ciphertext. |
| `Transaction` imutável | Auditabilidade total. `setUpdatedAt` sobrescrito como no-op para impedir alterações acidentais. |
| Wallet criada via evento | Desacopla `User` e `Wallet`. `CreateWalletConsumer` reage ao `UserCreatedEvent` de forma assíncrona. |
| `shared.query` com interfaces | `AuthorizationService` depende de interfaces, não de implementações concretas de outros contextos. |
| `UserSummary` / `WalletSummary` em `shared` | Contratos de leitura transversais sem vazar entidades entre bounded contexts. |
| `TransferStatus` em `shared.type` | Usado em múltiplos contextos e eventos — não pertence a nenhum contexto isolado. |
| Lock pessimista determinístico | Previne deadlocks ao garantir ordem total de aquisição de locks entre carteiras. |
| Outbox Pattern | Garante consistência entre persistência transacional e publicação Kafka sem two-phase commit. |
| JWT + Redis | Autenticação stateless com capacidade de revogação imediata via JTI. |
| Rate Limiting distribuído | Bucket4j + Redis protege contra abuso em ambiente multi-instância sem estado local. |
| DLT por tópico | Mensagens com falha são isoladas sem bloquear o consumo do tópico principal. |

---

## 🤝 Contribuindo

Contribuições são bem-vindas! Para mudanças significativas, abra uma issue primeiro para discutir o que você gostaria de alterar.

1. Faça um fork do projeto
2. Crie uma branch para sua feature (`git checkout -b feat/minha-feature`)
3. Commit suas alterações (`git commit -m 'feat: adiciona minha feature'`)
4. Push para a branch (`git push origin feat/minha-feature`)
5. Abra um Pull Request

> Siga o padrão [Conventional Commits](https://www.conventionalcommits.org/) para mensagens de commit.

---

## 📄 Licença

Este projeto está sob a licença [MIT](LICENSE).
