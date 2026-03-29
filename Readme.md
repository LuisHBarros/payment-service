# payment-service

API REST para pagamentos, carteiras digitais e transferencias entre usuarios, com Spring Boot, PostgreSQL, Kafka, Redis e padrao outbox.

Last updated: 2026-03-29

## Visao geral

O projeto segue uma separacao por modulos de dominio (`auth`, `user`, `wallet`, `transfer`, `transaction`) e usa eventos para desacoplar operacoes criticas. A criacao de transferencias e depositos persiste o estado de negocio no banco e delega a publicacao assincrona para a tabela de outbox.

Hoje o sistema suporta:

- autenticacao stateless com JWT e blacklist de tokens no Redis;
- ownership checks e autorizacao por papel;
- rate limiting opcional com Bucket4j + Redis;
- transferencias com lock pessimista deterministico e idempotencia;
- depositos via Stripe, com webhook assinado;
- observabilidade com Actuator, Micrometer, Prometheus e Grafana.

## Stack

| Area | Tecnologia |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.5.12 |
| Build | Maven |
| Banco | PostgreSQL 16 |
| Mensageria | Kafka 7.5 |
| Cache | Redis 7 |
| Migracoes | Flyway |
| Seguranca | Spring Security, BCrypt, JJWT |
| Resiliencia provider | Resilience4j + Spring AOP |
| Observabilidade | Actuator, Micrometer, Prometheus, Grafana |
| Testes | JUnit 5, Spring Boot Test, Testcontainers |

## Modulos

| Modulo | Responsabilidade principal |
|---|---|
| `auth` | Login, logout, resolucao do usuario autenticado e blacklist de tokens |
| `user` | Cadastro, consulta, alteracao e exclusao de usuarios |
| `wallet` | Carteiras, depositos, integracao com payment provider e webhook |
| `transfer` | Criacao e consulta de transferencias |
| `transaction` | Ledger de debitos e creditos |
| `shared` | Eventos, outbox, Kafka, criptografia e metricas |

## Fluxo assincrono

### Transferencias

1. `POST /api/v1/transfers` valida ownership e pre-condicoes.
2. `CreateTransferService` persiste `TransferEntity` com status `PENDING`.
3. O evento `TRANSFER_CREATED` e salvo na outbox.
4. `OutboxPublisher` publica `TransferCreatedEvent` no topico `payment.transfer.created`.
5. `TransferWalletConsumer` processa debito e credito com lock deterministico e idempotencia.
6. Eventos `WalletDebitedEvent` e `WalletCreditedEvent` sao publicados pela outbox.
7. `TransferConsumer` grava o ledger em `transaction`.
8. `TransferStatusConsumer` atualiza o status final da transferencia.

### Depositos

1. `POST /api/v1/wallets/{userId}/deposits` cria o deposito para a carteira informada.
2. `StripePaymentProvider` cria um `PaymentIntent` e retorna `clientSecret`.
3. O webhook `POST /api/v1/webhooks/deposits` valida a assinatura do provider.
4. `ProcessDepositService` marca o deposito e grava `DEPOSIT_COMPLETED` na outbox.
5. `DepositConsumer` cria a entrada `CREDIT` no ledger.

## API HTTP

### Auth

| Metodo | Rota | Acesso | Observacoes |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Publico | Login por email ou documento |
| `GET` | `/api/v1/auth/me` | Autenticado | Retorna o usuario atual |
| `POST` | `/api/v1/auth/logout` | Autenticado | Revoga o token atual |

### Users

| Metodo | Rota | Acesso | Observacoes |
|---|---|---|---|
| `POST` | `/api/v1/users` | Publico | Cria usuario; tipo e derivado do documento |
| `GET` | `/api/v1/users` | `ADMIN` | Lista paginada |
| `GET` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Ownership check no controller |
| `PATCH` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Atualiza email e/ou senha |
| `DELETE` | `/api/v1/users/{id}` | Owner ou `ADMIN` | Remove o registro |

### Wallets e Depositos

| Metodo | Rota | Acesso | Observacoes |
|---|---|---|---|
| `GET` | `/api/v1/wallets/{userId}` | Owner ou `ADMIN` | Retorna a carteira do usuario |
| `POST` | `/api/v1/wallets/{userId}/deposits` | Owner ou `ADMIN` | Body inclui `amount`, `walletId` e `paymentProvider` |
| `GET` | `/api/v1/wallets/{userId}/deposits` | Owner ou `ADMIN` | Lista depositos do usuario |
| `POST` | `/api/v1/webhooks/deposits` | Publico | Webhook do provider com assinatura validada |

### Transfers

| Metodo | Rota | Acesso | Observacoes |
|---|---|---|---|
| `POST` | `/api/v1/transfers` | `COMMON` ou `ADMIN` | `sourceWalletId` deve pertencer ao usuario autenticado |
| `GET` | `/api/v1/transfers` | Autenticado | Requer `walletId`; aceita filtros `status`, `type`, `startDate`, `endDate` |

### Transactions

| Metodo | Rota | Acesso | Observacoes |
|---|---|---|---|
| `GET` | `/api/v1/transactions` | Autenticado | Consulta por `walletId` ou `transferId`; pagina com sort por `createdAt desc` |
| `GET` | `/api/v1/transactions/{id}` | Autenticado | Faz ownership check pela carteira da transacao |

### Actuator

Os endpoints abaixo estao liberados em `SecurityConfig`:

- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

## Resiliencia e consistencia

### Outbox

- `OutboxPublisher` roda com `fixedDelay` configuravel por `outbox.poll-interval`.
- Cada item espera confirmacao de Kafka por `outbox.kafka-ack-timeout-ms`.
- Falhas incrementam `attempts`; acima de `outbox.max-attempts` o item e recuperado ou marcado como processado.
- Ha limpeza periodica de itens processados por `outbox.cleanup.retention-days`.

### Kafka consumers

- `KafkaConsumerConfig` aplica retry antes de DLT com `FixedBackOff`.
- Os valores sao externalizados por `KAFKA_RETRY_BACKOFF_MS` e `KAFKA_MAX_ATTEMPTS`.

### Payment provider

`StripePaymentProvider.createDeposit(...)` usa:

- `@Retry(name = "paymentProvider", fallbackMethod = "createDepositFallback")`
- `@CircuitBreaker(name = "paymentProvider", fallbackMethod = "createDepositFallback")`

Configuracao atual em `application.yaml`:

- retry com 3 tentativas;
- `waitDuration` inicial de `500ms`;
- backoff exponencial com multiplicador `2`;
- retry apenas para `PaymentProviderException`;
- `WebhookSignatureException` e `InvalidPaymentProviderException` sao ignoradas;
- circuit breaker abre com `failureRateThreshold: 50`, `minimumNumberOfCalls: 5` e `slidingWindowSize: 10`.

O fallback levanta `PaymentProviderException` com mensagem de indisponibilidade temporaria.

## Observabilidade

### Micrometer e Actuator

Metricas customizadas sao registradas em `shared/metrics/PaymentMetrics`:

- `payment.transfers.created.total`
- `payment.transfers.completed.total`
- `payment.transfers.failed.total`
- `payment.transfers.amount`
- `payment.outbox.published.total`
- `payment.outbox.failed.total`
- `payment.outbox.latency`
- `payment.users.created.total`

### Prometheus e Grafana

O `docker-compose.yml` sobe:

- `prometheus` em `http://localhost:9090`
- `grafana` em `http://localhost:3000`

Provisioning versionado:

- datasource: `grafana/provisioning/datasources/prometheus.yml`
- dashboards: `grafana/dashboards/application-api.json`, `grafana/dashboards/business-metrics.json`, `grafana/dashboards/infrastructure-jvm.json`

### Logging

`logback-spring.xml` usa console appender com padrao textual simples. Nao ha encoder JSON ativo no estado atual do codigo.

## Como executar localmente

### 1. Preparar variaveis

Copie `.env.example` para `.env` e preencha principalmente:

```env
APP_CRYPTO_SECRET=0123456789abcdef
JWT_SECRET=base64-secret
STRIPE_SECRET_KEY=sk_test_xxx
PAYMENT_WEBHOOK_SECRET=whsec_xxx
```

### 2. Subir infraestrutura

```bash
docker compose up --build
```

Servicos principais:

- app: `http://localhost:8080`
- postgres: `localhost:5432`
- kafka: `localhost:9092`
- redis: `localhost:6379`
- prometheus: `http://localhost:9090`
- grafana: `http://localhost:3000`

### 3. Endpoints uteis

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8080/actuator/metrics
```

### 4. OpenAPI e Swagger UI

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- JSON OpenAPI: `http://localhost:8080/v3/api-docs`
- YAML OpenAPI: `http://localhost:8080/v3/api-docs.yaml`

Para testar endpoints protegidos pela UI, faca login em `POST /api/v1/auth/login`, copie o JWT retornado e use o botao `Authorize` com o valor `Bearer <token>`.

## Testes

Suite atual:

- unitarios para servicos, filtros JWT, consumers e provider Stripe;
- integracao com Spring Boot/Testcontainers, incluindo `AuthControllerIT`, `TransferControllerIT`, `TransferFlowIT` e `WalletControllerIT`;
- `PaymentProviderResilienceTest` cobre retry, excecoes ignoradas e estados do circuit breaker.

Executar localmente:

```bash
./mvnw test
./mvnw verify
```

Estado do CI:

- `.github/workflows/ci.yml` ainda executa apenas `mvn test -B`;
- integration tests do Failsafe ainda nao rodam no GitHub Actions.

## Scripts uteis

| Script | Uso |
|---|---|
| `seed.sh` | Cria usuarios, injeta saldo via banco e dispara transferencias de exemplo |
| `load.sh` | Gera carga de transferencias por um periodo configuravel |

## Documentos adicionais

- `structure.md`: visao de modulos, endpoints e topicos
- `decisions.md`: decisoes tecnicas implementadas e tradeoffs atuais
- `todo.md`: pendencias e proximos passos priorizados
