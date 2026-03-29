# Serviço de Pagamentos - Estrutura

Última atualização: 2026-03-29

## Organização de pacotes

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

## Responsabilidades dos módulos

### `auth`

- `AuthController` expõe login, logout e `me`.
- `AuthService` autentica por e-mail ou documento.
- `JwtService` emite tokens e valida claims de expiração.
- `JwtAuthenticationFilter` resolve o principal autenticado para rotas protegidas.

### `config`

- `SecurityConfig` define rotas públicas, ordem do filtro JWT, CORS e política de sessão stateless.
- `CorsProperties` externaliza origens, métodos, headers e credenciais de CORS via `application.yaml`.
- `RateLimitConfig`, `RateLimitFilter` e `RateLimitProperties` fornecem limitação de taxa opcional.
- `GlobalExceptionHandler` mapeia exceções de domínio para respostas HTTP.
- `SecurityUtils.requireOwnership(...)` é a guarda padrão de ownership entre os controllers.

### `shared`

- `shared/config` contém a configuração de consumers e tópicos do Kafka.
- `shared/entity/OutboxEntity` e `shared/repository/OutboxRepository` sustentam a tabela de outbox.
- `shared/kafka/KafkaEventProducer` envia eventos tipados para o Kafka.
- `shared/kafka/OutboxPublisher` busca linhas pendentes, aguarda os acks do Kafka, registra métricas e executa recuperação/limpeza.
- `shared/metrics/PaymentMetrics` centraliza contadores e timers customizados do Micrometer.
- `shared/query` expõe contratos de leitura entre módulos usados por controllers e services.

### `user`

- Persiste usuários com conversores criptografados de e-mail e documento.
- Deriva `UserType` a partir de CPF ou CNPJ.
- Publica `UserCreatedEvent` após a criação do usuário.

### `wallet`

- `CreateWalletConsumer` cria uma carteira por usuário a partir de `UserCreatedEvent`.
- `ProcessTransferService` executa a mutação de saldo com ordenação determinística de locks.
- `ProcessedTransferEntity` evita processamento duplicado de transferências.
- `DepositController` gerencia criação de depósitos, listagem e tratamento de webhook.
- `provider/StripePaymentProvider` é o único provedor de pagamento implementado atualmente.

### `transfer`

- `CreateTransferService` valida pré-condições de negócio, persiste `TransferEntity` como `PENDING` e grava `TRANSFER_CREATED` na outbox.
- `GetTransferService` consulta transferências com filtros opcionais.
- `TransferStatusConsumer` consome atualizações de status e mantém `TransferEntity` sincronizada.

### `transaction`

- `TransferConsumer` grava lançamentos de débito e crédito no razão a partir de eventos de carteira.
- `DepositConsumer` grava entradas `CREDIT` para depósitos bem-sucedidos.
- `TransactionController` expõe consultas paginadas do razão por carteira ou transferência.

## Superfície HTTP

| Método | Rota | Acesso | Observações |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Público | Login com `identifier` + `password` |
| `GET` | `/api/v1/auth/me` | Autenticado | Retorna o usuário atual |
| `POST` | `/api/v1/auth/logout` | Autenticado | Adiciona o JWT atual à blacklist |
| `POST` | `/api/v1/users` | Público | Cria usuário |
| `GET` | `/api/v1/users` | `ADMIN` | Lista paginada |
| `GET` | `/api/v1/users/{id}` | Dono ou `ADMIN` | Verificação de ownership no controller |
| `PATCH` | `/api/v1/users/{id}` | Dono ou `ADMIN` | Atualização parcial |
| `DELETE` | `/api/v1/users/{id}` | Dono ou `ADMIN` | Exclui o registro |
| `GET` | `/api/v1/wallets/{userId}` | Dono ou `ADMIN` | Retorna a carteira do usuário |
| `POST` | `/api/v1/wallets/{userId}/deposits` | Dono ou `ADMIN` | Cria depósito |
| `GET` | `/api/v1/wallets/{userId}/deposits` | Dono ou `ADMIN` | Lista depósitos |
| `POST` | `/api/v1/webhooks/deposits` | Público | Valida a assinatura do provedor |
| `POST` | `/api/v1/transfers` | `COMMON` ou `ADMIN` | Cria transferência |
| `GET` | `/api/v1/transfers` | Autenticado | Requer `walletId`; filtros opcionais |
| `GET` | `/api/v1/transactions` | Autenticado | Consulta por `walletId` ou `transferId` |
| `GET` | `/api/v1/transactions/{id}` | Autenticado | Retorna a transação por id |
| `GET` | `/actuator/health` | Público | Endpoint de health |
| `GET` | `/actuator/info` | Público | Endpoint de info |
| `GET` | `/actuator/metrics` | Público | Índice de métricas |
| `GET` | `/actuator/prometheus` | Público | Endpoint de scrape do Prometheus |

## Tópicos Kafka e handlers assíncronos

| Tópico | Evento | Produtor | Consumer |
|---|---|---|---|
| `payment.users` | `UserCreatedEvent` | módulo `user` via outbox | `wallet.CreateWalletConsumer` |
| `payment.transfer.created` | `TransferCreatedEvent` | módulo `transfer` via outbox | `wallet.TransferWalletConsumer` |
| `payment.wallet.debits` | `WalletDebitedEvent` | módulo `wallet` via outbox | `transaction.TransferConsumer` |
| `payment.wallet.credits` | `WalletCreditedEvent` | módulo `wallet` via outbox | `transaction.TransferConsumer` |
| `payment.transfer.status` | `TransferStatusChangedEvent` | módulo `transaction` via outbox | `transfer.TransferStatusConsumer` |
| `payment.deposit.completed` | `DepositCompletedEvent` | módulo `wallet` via outbox | `transaction.DepositConsumer` |

## Regras de runtime que valem preservar

- Ownership é aplicado por meio de `SecurityUtils.requireOwnership(...)`, não por filtragem no repositório.
- A limitação de taxa é opcional e só é anexada quando o bean do filtro está disponível.
- As tentativas de retry dos consumers Kafka são configuradas em `KafkaConsumerConfig` com `FixedBackOff` antes da publicação em DLT.
- O relay da outbox aguarda de forma síncrona o ack do Kafka por registro e marca registros esgotados como processados após a recuperação.
- A criação de depósitos com Stripe usa retry e circuit breaker do Resilience4j com fallback compartilhado.
- `PaymentProviderName` atualmente suporta apenas `STRIPE`.

## Arquivos operacionais

| Arquivo | Finalidade |
|---|---|
| `src/main/resources/application.yaml` | Configuração principal de runtime, CORS, Kafka, Redis, rate limit e Resilience4j |
| `src/main/resources/logback-spring.xml` | Padrão de logs no console |
| `docker-compose.yml` | Stack local com app, postgres, kafka, redis, prometheus e grafana |
| `prometheus.yml` | Configuração de scrape para `payment-service:8080/actuator/prometheus` |
| `grafana/provisioning/datasources/prometheus.yml` | Provisionamento de datasource do Grafana |
| `grafana/provisioning/dashboards/dashboards.yml` | Provisionamento de dashboards do Grafana |
| `seed.sh` | Fluxo local de carga inicial com usuários e transferências de exemplo |
| `load.sh` | Gerador simples de carga para transferências |
