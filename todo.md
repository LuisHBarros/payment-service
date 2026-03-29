# TODO do Projeto - Payment Service

Última atualização: 2026-03-29

## Concluídos Recentemente

- Autenticação JWT com blacklist de tokens no Redis para logout.
- Verificação de ownership e autorização por papel em endpoints de user, wallet e transfer.
- Rate limiting com Bucket4j + Redis para rotas públicas e autenticadas.
- `GlobalExceptionHandler` e substituição de falhas brutas por exceções de domínio.
- Padrão outbox para publicação Kafka, com polling, limite de retries e limpeza.
- Migração Flyway `V1__init_schema.sql` para schema e índices iniciais.
- Paginação em `GET /api/v1/users`.
- Renomeação de `TransactionController` para `TransferController`.
- Limpeza Docker: `.dockerignore` e credenciais do compose externalizadas.
- CI com GitHub Actions em `.github/workflows/ci.yml`.
- Scaffolding de testes de integração com Testcontainers:
  `AuthControllerIT`, `TransferControllerIT`, `TransferFlowIT`, `WalletControllerIT`.
- Kafka producer com tratamento de envio async via `.whenComplete(...)`.
- Kafka consumers separados por tipo de evento/tópico em vez de branching em tópico compartilhado.
- `RateLimitProperties.getEndpointLimit(...)` retorna `null` para rotas sem mapeamento.
- Uso de `@Transactional` consistente com Spring.
- Módulo de depósito com integração de payment provider (Stripe).
- Verificação de assinatura de webhook para payment providers.
- Stack de observabilidade com Prometheus e Grafana.
- Métricas customizadas para transfers, depósitos e operações de wallet.
- Dashboards Grafana customizados (Business, API, Infra) com provisioning automático.
- Prometheus com scrape interval de 30s e volumes persistentes.
- Logging estruturado em JSON com Logback.
- Scripts de seeding e load testing do banco.
- Endpoints de ledger de transações (`GET /api/v1/transactions`) com paginação, filtros (walletId, transferId, tipo, intervalo de datas) e verificação de ownership.
- Configuração CORS via `CorsConfigurationSource` em `SecurityConfig.java`.

---

## Problemas Críticos

Nenhum problema crítico em aberto.

---

## Alta Prioridade

| # | Issue | Status | Observações |
|---|-------|--------|-------------|
| 1 | Configuração CORS ausente | **RESOLVIDO** | `CorsConfigurationSource` em `SecurityConfig.java:67-77` com origins `localhost:3000/3333` |
| 2 | Cobertura de testes de auth/security incompleta | **PARCIAL** | `JwtServiceTest` (7), `JwtAuthenticationFilterTest` (7) e `SecurityUtilsTest` (4) adicionados; `RateLimitFilter` deixado de fora por exigir Redis real via Testcontainers + Bucket4j proxy manager, o que eleva complexidade e tempo de runtime significativamente |
| 3 | Testes de Kafka consumer e outbox ausentes | **PARCIAL** | `TransferFlowIT` cobre consumers indiretamente. Agora 5 consumers têm testes unitários dedicados: `CreateWalletConsumerTest`, `TransferWalletConsumerTest`, `TransferConsumerTest`, `DepositConsumerTest`, `TransferStatusConsumerTest`. `OutboxPublisher` continua sem testes diretos |
| 4 | CI executa apenas `mvn test` | ABERTO | `.github/workflows/ci.yml` ainda usa `mvn test -B`; integration tests (failsafe) não rodam no CI |
| 5 | Segurança do webhook de depósito | **RESOLVIDO** | 6 testes em 2 arquivos cobrem cenários de assinatura válida, inválida e evento não suportado |
| 6 | Tratamento de erros do payment provider | ABERTO | `@EnableRetry` declarado sem `@Retryable`; sem circuit breaker; sem fallback provider |

---

## Média Prioridade

| # | Issue | Status | Observações |
|---|-------|--------|-------------|
| 7 | Regras de senha fracas | ABERTO | `Password` só exige não vazio + mínimo de 5 caracteres, sem complexidade |
| 8 | `AesEncryptor` usa AES-CBC | ABERTO | Considerar AES-GCM para criptografia autenticada |
| 9 | `AesEncryptor` usa charset padrão da plataforma | ABERTO | 3 chamadas `getBytes()`/`new String()` sem `StandardCharsets.UTF_8` |
| 10 | Estilos de injeção de dependência mistos | **RESOLVIDO** | 8 serviços migrados para `@RequiredArgsConstructor`: `CreateUserService`, `DeleteUserService`, `GetUserService`, `UpdatePasswordService`, `PatchUserService`, `UpdateUserEmailService`, `CreateWalletService`, `GetWalletService` |
| 11 | Nomenclatura inconsistente de pacotes de exceção | **RESOLVIDO** | `user.exceptions` renomeado para `user.exception` (singular), alinhando com `wallet.exception`, `transfer.exception`, `transaction.exception` |
| 12 | `TransferAuthorizationServiceTest` precisa de limpeza | **RESOLVIDO** | Indentação padronizada em 4 spaces, blank lines uniformes, formatação consistente |
| 13 | Valores de retry/backoff ainda parcialmente hardcodados | **RESOLVIDO** | `FixedBackOff(1000L, 3)` externalizado para `application.yaml` com env vars `KAFKA_RETRY_BACKOFF_MS` e `KAFKA_MAX_ATTEMPTS` |
| 14 | Provedores de pagamento adicionais necessários | ABERTO | Somente Stripe implementado; considerar PayPal, Mercado Pago, etc. |
| 15 | Dashboards Grafana precisam de customização | **RESOLVIDO** | 3 dashboards versionados: business-metrics, application-api, infrastructure-jvm; provisioning config em `grafana/provisioning/dashboards/` |
| 16 | Intervalo de scrape do Prometheus | **RESOLVIDO** | `prometheus.yml` com `scrape_interval: 30s`, `scrape_timeout: 10s`, `evaluation_interval: 30s` |
| 17 | Sistema de notificação de depósitos | ABERTO | Nenhum serviço de notificação encontrado (email, push, etc.) |

---

## Snapshot de Cobertura de Testes

| Área | Status | Observações |
|------|--------|-------------|
| user services | BOM | Testes unitários cobrem fluxos principais |
| wallet services | BOM | Lógica de criação/busca de wallet coberta |
| transfer services | BOM | `GetTransferServiceTest` e `TransferAuthorizationServiceTest` existem |
| deposit services | MODERADO | Testes básicos de criação e processamento de depósito existem |
| auth | BOM | `AuthControllerIT` + `JwtServiceTest` (7) + `JwtAuthenticationFilterTest` (7) |
| controllers (integração) | MODERADO | Suite básica para auth, transfers, wallet e fluxo feliz |
| payment providers | MODERADO | Testes para Stripe existem |
| Kafka consumers | BOM | 5 consumers com testes unitários dedicados (`CreateWallet`, `TransferWallet`, `Transfer`, `Deposit`, `TransferStatus`) |
| Kafka producer / outbox | BAIXO | Sem testes diretos para callback de producer ou polling/limpeza de outbox |
| rate limiting | BAIXO | Sem testes focados em throttling; deixado de fora por complexidade (Bucket4j + Redis via Testcontainers) |
| helpers de autorização | BOM | `SecurityUtilsTest` (4 testes) cobre ownership e bypass ADMIN |
| observabilidade | BAIXO | Sem testes para coleta de métricas e logging |

---

## Próximos Passos Recomendados

1. ~~Adicionar testes focados para JWT auth, rate limiting, checagens de ownership e listeners Kafka.~~ JWT auth (14 testes), ownership (4 testes) e Kafka consumers (12 testes) implementados. `RateLimitFilter` deixado de fora por exigir Redis real + Bucket4j proxy manager (complexidade e tempo de runtime elevados).
2. Alterar CI de `mvn test` para `mvn verify` para que integration tests rodem no GitHub Actions.
3. Implementar retry/circuit breaker para chamadas ao payment provider (`@Retryable` já habilitado mas não utilizado).
4. Melhorar tratamento de erros e estratégias de recuperação do payment provider.
5. Refatorar `AesEncryptor` para AES-GCM com `StandardCharsets.UTF_8`.
6. ~~Padronizar nomenclatura de pacotes de exceção (`user.exceptions` → `user.exception`).~~
7. ~~Padronizar estilo de injeção de dependência (escolher entre construtor explícito ou `@RequiredArgsConstructor`).~~
8. Fortalecer regras de senha (complexidade, mínimo de caracteres).
9. ~~Externalizar valores de retry/backoff do Kafka consumer para `application.yaml`.~~
10. Implementar provedores de pagamento adicionais (PayPal, Mercado Pago, etc.).
11. Implementar sistema de notificação de depósitos (email, push, etc.).
12. Adicionar Node Exporter e cAdvisor para métricas de host e containers.
