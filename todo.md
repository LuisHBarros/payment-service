# TODO do projeto - payment-service

Last updated: 2026-03-29 (14:15)

## Concluido recentemente

- UserCreatedEvent migrado para padrão outbox para consistência transacional e confiabilidade (2026-03-29).
- JWT stateless com blacklist de tokens no Redis para logout.
- Ownership checks e autorizacao por papel nos endpoints principais.
- Rate limiting opcional com Bucket4j + Redis.
- Outbox com polling, retry, timeout de ack, recovery e limpeza.
- Flyway com schema inicial.
- Suite basica de integracao com Testcontainers.
- Depositos com Stripe e validacao de assinatura de webhook.
- Observabilidade com Prometheus, Grafana, Actuator e metricas customizadas.
- Dashboards Grafana versionados e provisionados automaticamente.
- Retry e circuit breaker no payment provider com Resilience4j.
- Externalizacao dos valores de retry/backoff do consumidor Kafka.

## Problemas criticos

Nenhum problema critico aberto no estado atual do repositorio.

## Alta prioridade

| # | Item | Status | Observacoes |
|---|---|---|---|
| 1 | Cobertura de auth/security ainda parcial | PARCIAL | JWT e ownership ja possuem testes dedicados; `RateLimitFilter` segue sem suite focada |
| 2 | CI executa `mvn verify` | RESOLVIDO | `.github/workflows/ci.yml` agora roda `./mvnw --batch-mode --no-transfer-progress verify` e publica relatorios de teste |
| 3 | Outbox sem testes diretos | MÉDIO | Testes especificos adicionados para CreateUserService/UserCreatedEvent; `OutboxPublisher` ainda tem suite propria parcial |
| 4 | Webhook de deposito | RESOLVIDO | Assinatura validada e cenarios principais cobertos por testes |
| 5 | Tratamento de erros do payment provider | RESOLVIDO | Resilience4j com retry, circuit breaker, fallback e testes dedicados |

## Media prioridade

| # | Item | Status | Observacoes |
|---|---|---|---|
| 6 | Regras de senha ainda fracas | ABERTO | `Password` exige minimo baixo e sem politica de complexidade |
| 7 | `AesEncryptor` ainda usa AES-CBC | RESOLVIDO | Migrado para AES-GCM com charset explicito |
| 8 | Charset explicito em criptografia | RESOLVIDO | `AesEncryptor` agora usa `StandardCharsets.UTF_8` |
| 9 | Payment providers adicionais | ABERTO | Apenas `STRIPE` esta implementado |
| 10 | Notificacao de depositos | ABERTO | Nao existe envio de email, push ou webhook de saida |
| 11 | Observabilidade de host e containers | ABERTO | Node Exporter e cAdvisor ainda nao estao na stack |

## Snapshot de testes

| Area | Estado | Observacoes |
|---|---|---|
| Servicos de usuario | BOM | Fluxos principais cobertos |
| Servicos de wallet | BOM | Criacao, consulta e parte do fluxo de deposito cobertos |
| Servicos de transfer | BOM | Autorizacao e consultas cobertas |
| Auth/JWT | BOM | `AuthControllerIT`, `JwtServiceTest` e `JwtAuthenticationFilterTest` |
| Kafka consumers | BOM | Consumers principais possuem testes dedicados |
| Payment provider | BOM | Stripe e resiliencia cobertos |
| Criptografia | BOM | `AesEncryptorTest` e `DocumentConverterTest` adicionados |
| Outbox publisher | MÉDIO | Testes especificos adicionados para CreateUserService; OutboxPublisher ainda precisa de suite propria completa |
| Rate limiting | BAIXO | Sem testes focados de throttling |
| Observabilidade | BAIXO | Sem testes de metricas ou logging |

## Proximos passos recomendados

1. Criar testes diretos para `OutboxPublisher`, cobrindo sucesso, timeout de ack, max-attempts e cleanup. *Parcialmente resolvido: testes para CreateUserService/UserCreatedEvent adicionados*
2. Cobrir `RateLimitFilter` com testes que validem throttling real contra Redis/Testcontainers.
3. Fortalecer a politica de senha.
4. Revisar `AesEncryptor` para charset explicito e possivel migracao de CBC para GCM. *RESOLVIDO: Migrado para AES-GCM com StandardCharsets.UTF_8 e testes completos adicionados*
5. Adicionar novos providers de pagamento atras da interface `PaymentProvider`.
6. Planejar notificacoes de deposito.
7. Evoluir a observabilidade com metricas de host/container.
