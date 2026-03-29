# payment-service

> API REST para pagamentos, carteiras digitais e transferências entre usuários.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.12-brightgreen?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)
![Kafka](https://img.shields.io/badge/Kafka-7.5-black?logo=apachekafka)
![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## Visão geral

O projeto é estruturado em contextos delimitados (`auth`, `user`, `wallet`, `transfer`, `transaction`) e usa eventos para desacoplar operações críticas. Toda mutação de negócio persiste primeiro no banco e delega a publicação assíncrona para a tabela de outbox, que faz polling e entrega garantida para o Kafka.

Funcionalidades implementadas:

- autenticação stateless com JWT e blacklist de tokens no Redis;
- ownership checks e autorização por papel (`COMMON`, `MERCHANT`, `ADMIN`);
- rate limiting opcional com Bucket4j + Redis;
- transferências com lock pessimista determinístico e idempotência via `ProcessedTransferEntity`;
- depósitos via Stripe (PaymentIntent) com webhook assinado;
- outbox com retry, timeout de ack, recovery e limpeza periódica;
- resiliência no provider de pagamento com Resilience4j (retry + circuit breaker);
- observabilidade com Actuator, Micrometer, Prometheus e Grafana.

---

## Stack

| Área | Tecnologia |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.5.12 |
| Build | Maven 3.9 |
| Banco de dados | PostgreSQL 16 |
| Mensageria | Kafka 7.5 (Confluent) |
| Cache / blacklist | Redis 7 |
| Migrações | Flyway |
| Segurança | Spring Security + BCrypt + JJWT 0.12.6 |
| Rate limiting | Bucket4j 8.11 + Lettuce |
| Payment provider | Stripe Java 26.3 |
| Resiliência | Resilience4j 2.2 + Spring AOP |
| Observabilidade | Actuator, Micrometer, Prometheus, Grafana |
| Testes | JUnit 5, Mockito, Spring Boot Test, Testcontainers 1.20 |

---

## Módulos

| Módulo | Responsabilidade principal |
|---|---|
| `auth` | Login, logout, resolução do usuário autenticado e blacklist de tokens |
| `user` | Cadastro, consulta, atualização e exclusão de usuários |
| `wallet` | Carteiras, depósitos, integração com payment provider e webhook |
| `transfer` | Criação, consulta e atualização de status de transferências |
| `transaction` | Ledger de débitos e créditos |
| `shared` | Eventos, outbox, Kafka, criptografia e métricas |

---

## Fluxos assíncronos

### Transferências

```
POST /api/v1/transfers
  → CreateTransferService   — valida ownership, persiste TransferEntity (PENDING)
  → OutboxPublisher         — publica TransferCreatedEvent em payment.transfer.created
  → TransferWalletConsumer  — debita e credita com lock determinístico + idempotência
  → OutboxPublisher         — publica WalletDebitedEvent e WalletCreditedEvent
  → TransferConsumer        — grava entradas DEBIT/CREDIT no ledger
  → OutboxPublisher         — publica TransferStatusChangedEvent
  → TransferStatusConsumer  — atualiza status final da TransferEntity
```

### Depósitos

```
POST /api/v1/wallets/{userId}/deposits
  → CreateDepositService    — cria DepositEntity (PENDING)
  → StripePaymentProvider   — cria PaymentIntent, retorna clientSecret
  ← frontend coleta pagamento usando clientSecret

POST /api/v1/webhooks/deposits  (Stripe → aplicação)
  → validação de assinatura (Stripe-Signature)
  → ProcessDepositService   — marca depósito como COMPLETED, grava DEPOSIT_COMPLETED na outbox
  → DepositConsumer         — grava entrada CREDIT no ledger
```

---

## Tópicos Kafka

| Tópico | Evento | Produtor | Consumidor |
|---|---|---|---|
| `payment.users` | `UserCreatedEvent` | `user` via outbox | `wallet.CreateWalletConsumer` |
| `payment.transfer.created` | `TransferCreatedEvent` | `transfer` via outbox | `wallet.TransferWalletConsumer` |
| `payment.wallet.debits` | `WalletDebitedEvent` | `wallet` via outbox | `transaction.TransferConsumer` |
| `payment.wallet.credits` | `WalletCreditedEvent` | `wallet` via outbox | `transaction.TransferConsumer` |
| `payment.transfer.status` | `TransferStatusChangedEvent` | `transaction` via outbox | `transfer.TransferStatusConsumer` |
| `payment.deposit.completed` | `DepositCompletedEvent` | `wallet` via outbox | `transaction.DepositConsumer` |

---

## API HTTP

### Auth

| Método | Rota | Acesso | Observações |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Público | Login por `identifier` (email ou documento) + `password` |
| `GET` | `/api/v1/auth/me` | Autenticado | Retorna o usuário atual |
| `POST` | `/api/v1/auth/logout` | Autenticado | Revoga o token atual via blacklist no Redis |

### Users

| Método | Rota | Acesso | Observações |
|---|---|---|---|
| `POST` | `/api/v1/users` | Público | Cria usuário; tipo derivado do documento (CPF → COMMON, CNPJ → MERCHANT) |
| `GET` | `/api/v1/users` | `ADMIN` | Lista paginada |
| `GET` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Ownership check no controller |
| `PATCH` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Atualiza email e/ou senha |
| `DELETE` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Remove o registro |

### Wallets e Depósitos

| Método | Rota | Acesso | Observações |
|---|---|---|---|
| `GET` | `/api/v1/wallets/{userId}` | Owner ou `ADMIN` | Retorna a carteira do usuário |
| `POST` | `/api/v1/wallets/{userId}/deposits` | Owner ou `ADMIN` | Body inclui `amount`, `walletId` e `paymentProvider`; retorna `clientSecret` |
| `GET` | `/api/v1/wallets/{userId}/deposits` | Owner ou `ADMIN` | Lista depósitos do usuário |
| `POST` | `/api/v1/webhooks/deposits` | Público | Webhook do provider com assinatura validada via `Stripe-Signature` |

### Transfers

| Método | Rota | Acesso | Observações |
|---|---|---|---|
| `POST` | `/api/v1/transfers` | `COMMON` ou `ADMIN` | `sourceWalletId` deve pertencer ao usuário autenticado |
| `GET` | `/api/v1/transfers` | Autenticado | Requer `walletId`; aceita filtros `status`, `type`, `startDate`, `endDate` |

### Transactions

| Método | Rota | Acesso | Observações |
|---|---|---|---|
| `GET` | `/api/v1/transactions` | Autenticado | Consulta por `walletId` ou `transferId`; paginado por `createdAt desc` |
| `GET` | `/api/v1/transactions/{id}` | Autenticado | Ownership check pela carteira da transação |

### Actuator (públicos)

```
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
GET /actuator/prometheus
```

---

## Resiliência e consistência

### Outbox

- `OutboxPublisher` roda com `fixedDelay` configurável por `outbox.poll-interval`.
- Cada item aguarda confirmação do Kafka por `outbox.kafka-ack-timeout-ms`.
- Falhas incrementam `attempts`; acima de `outbox.max-attempts` o item é marcado como processado após recovery.
- Limpeza periódica de itens processados por `outbox.cleanup.retention-days`.

### Lock determinístico em transferências

`ProcessTransferService` ordena as carteiras pelo menor UUID antes de adquirir lock pessimista, eliminando deadlocks em transferências cruzadas concorrentes.

### Idempotência

`ProcessedTransferEntity` registra eventos já processados, protegendo o ledger contra reprocessamento em retries de consumidor ou DLT.

### Kafka — retry e DLT

`KafkaConsumerConfig` aplica `FixedBackOff` com `DeadLetterPublishingRecoverer` antes de mover mensagens para o dead-letter topic. Os valores de backoff são externalizados por `KAFKA_RETRY_BACKOFF_MS` e `KAFKA_MAX_ATTEMPTS`.

### Resilience4j no Stripe

`StripePaymentProvider.createDeposit(...)` usa `@Retry` e `@CircuitBreaker` com configuração em `application.yaml`:

- 3 tentativas com `waitDuration: 500ms` e backoff exponencial (multiplicador 2);
- retry apenas para `PaymentProviderException`;
- `WebhookSignatureException` e `InvalidPaymentProviderException` são ignoradas (não fazem retry);
- circuit breaker abre com `failureRateThreshold: 50`, `minimumNumberOfCalls: 5`, `slidingWindowSize: 10`.

O fallback levanta `PaymentProviderException` com mensagem de indisponibilidade temporária.

---

## Observabilidade

### Métricas customizadas (`PaymentMetrics`)

| Métrica | Tipo | Tags |
|---|---|---|
| `payment.transfers.created.total` | Counter | — |
| `payment.transfers.completed.total` | Counter | — |
| `payment.transfers.failed.total` | Counter | `reason` |
| `payment.transfers.amount` | DistributionSummary | — |
| `payment.outbox.published.total` | Counter | `event_type` |
| `payment.outbox.failed.total` | Counter | `event_type` |
| `payment.outbox.latency` | Timer | — |
| `payment.users.created.total` | Counter | `type` |

### Dashboards Grafana (provisionados automaticamente)

| Dashboard | Arquivo | Conteúdo |
|---|---|---|
| Business Metrics | `business-metrics.json` | Transfers criadas/completadas/falhadas, volume, usuários por tipo |
| Application API | `application-api.json` | Req/s por endpoint, latência P50/P95/P99, distribuição de status HTTP |
| Infrastructure JVM | `infrastructure-jvm.json` | Heap, GC, CPU, threads, conexões de banco |

### Serviços locais

| Serviço | URL | Credenciais |
|---|---|---|
| Aplicação | http://localhost:8080 | — |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | `admin` / `admin` |

---

## Como executar localmente

### 1. Configurar variáveis de ambiente

```bash
cp .env.example .env
```

Preencha as chaves obrigatórias:

```env
APP_CRYPTO_SECRET=0123456789abcdef        # 16 bytes para AES
JWT_SECRET=<base64-encoded-secret>
STRIPE_SECRET_KEY=sk_test_xxx
PAYMENT_WEBHOOK_SECRET=whsec_xxx
```

### 2. Subir a stack

```bash
docker compose up --build
```

Serviços: `app:8080`, `postgres:5432`, `kafka:9092`, `redis:6379`, `prometheus:9090`, `grafana:3000`.

### 3. Popular dados de exemplo

```bash
chmod +x seed.sh load.sh
./seed.sh              # cria usuários, injeta saldo, dispara transferências de exemplo
./load.sh 120          # 120 segundos de carga contínua de transferências
```

### 4. Verificar saúde

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

---

## Testes

### Suíte atual

| Área | Estado | Observações |
|---|---|---|
| Serviços de usuário | ✅ bom | Fluxos principais cobertos |
| Serviços de wallet | ✅ bom | Criação, consulta e depósito cobertos |
| Serviços de transfer | ✅ bom | Autorização e consultas cobertas |
| Auth / JWT | ✅ bom | `AuthControllerIT`, `JwtServiceTest`, `JwtAuthenticationFilterTest` |
| Kafka consumers | ✅ bom | Consumers principais com testes dedicados |
| Payment provider | ✅ bom | Stripe e Resilience4j cobertos em `PaymentProviderResilienceTest` |
| Integração (Testcontainers) | ✅ bom | `AuthControllerIT`, `TransferControllerIT`, `TransferFlowIT`, `WalletControllerIT` |
| Outbox publisher | ⚠️ parcial | Fluxos de integração exercitam indiretamente; sem suite direta |
| Rate limiting | ⚠️ parcial | Sem testes focados de throttling real |

### Executar

```bash
./mvnw test      # unitários e integração com H2
./mvnw verify    # inclui testes de integração do Failsafe (Testcontainers)
```

> **CI:** `.github/workflows/ci.yml` executa apenas `mvn test -B`. Os testes de integração do Failsafe ainda não rodam no GitHub Actions.

---

## Pendências conhecidas

| Prioridade | Item | Status |
|---|---|---|
| Alta | CI executar `mvn verify` com integração no GitHub Actions | Aberto |
| Alta | Testes diretos para `OutboxPublisher` | Parcial |
| Alta | Testes de throttling para `RateLimitFilter` | Aberto |
| Média | Política de senha com requisitos de complexidade | Aberto |
| Média | Migrar `AesEncryptor` de AES-CBC para AES-GCM com charset explícito | Aberto |
| Média | Suporte a múltiplos payment providers (somente `STRIPE` implementado) | Aberto |
| Média | Notificações de depósito (email / push / webhook de saída) | Aberto |
| Baixa | Node Exporter + cAdvisor para métricas de host/container | Aberto |
| Baixa | Logging estruturado JSON (`logback-spring.xml` usa texto simples atualmente) | Aberto |
| Baixa | OpenTelemetry para distributed tracing end-to-end | Aberto |

---

## Documentação complementar

| Arquivo | Conteúdo |
|---|---|
| `decisions.md` | Decisões arquiteturais implementadas e seus invariantes |
| `structure.md` | Visão técnica de módulos, endpoints e tópicos (em inglês) |
| `observability_plan.md` | Plano de observabilidade, dashboards e PromQL de referência |
| `todo.md` | Pendências priorizadas com estado atual |

---

## Licença

MIT © 2026 [Luis Henrique de Barros](https://github.com/LuisHBarros)
