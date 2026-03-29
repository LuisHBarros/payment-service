# Decisoes tecnicas

Last updated: 2026-03-29

Este documento registra decisoes que ja estao refletidas no codigo atual. O objetivo aqui nao e manter uma cronologia completa, e sim deixar claro quais invariantes arquiteturais o projeto depende para continuar evoluindo sem regressao.

## 1. Integracao entre modulos por eventos e outbox

**Status:** implementado

**Decisao**

Persistir alteracoes de negocio primeiro e publicar eventos assincronos via tabela de outbox, em vez de chamar modulos vizinhos diretamente.

**Como isso aparece no codigo**

- `CreateTransferService` grava `TRANSFER_CREATED` na outbox.
- `ProcessDepositService` grava `DEPOSIT_COMPLETED` na outbox.
- `OutboxPublisher` faz polling da tabela, publica no Kafka e controla retries, recovery e cleanup.

**Motivo**

- reduz acoplamento entre contextos;
- evita depender de disponibilidade imediata do broker no caminho sincrono;
- permite recuperacao controlada para falhas de publicacao.

## 2. Processamento de transferencias com lock deterministico

**Status:** implementado

**Decisao**

Ao processar uma transferencia, a carteira com menor UUID e bloqueada primeiro para evitar deadlock.

**Como isso aparece no codigo**

- `ProcessTransferService` aplica ordenacao deterministica antes de debitar e creditar.
- `WalletRepository` oferece acesso com lock pessimista para mutacao de saldo.

**Motivo**

- preserva consistencia de saldo em cenarios concorrentes;
- reduz risco de deadlock entre transferencias cruzadas;
- mantem a regra simples o bastante para ser auditavel.

## 3. Idempotencia no consumo de eventos financeiros

**Status:** implementado

**Decisao**

Mensagens Kafka podem ser reprocessadas. Por isso, a mutacao de saldo precisa ser idempotente e a atualizacao de status precisa ignorar eventos repetidos.

**Como isso aparece no codigo**

- `ProcessedTransferEntity` e `ProcessedTransferRepository` evitam debito/credito duplicado.
- `TransferStatusConsumer` compara o status antes de salvar novamente.

**Motivo**

- evita efeito duplicado em retries de consumidor;
- suporta DLT e reprocessamento sem corromper o ledger.

## 4. Kafka com retry antes de DLT

**Status:** implementado

**Decisao**

Aplicar retry no consumo antes de enviar para dead-letter topic, com valores externalizados em configuracao.

**Como isso aparece no codigo**

- `KafkaConsumerConfig` usa `DefaultErrorHandler` com `DeadLetterPublishingRecoverer`.
- `FixedBackOff` le os valores de `spring.kafka.consumer.listener.retry-backoff-ms` e `max-attempts`.

**Motivo**

- falhas transitorias nao devem mover mensagens para DLT cedo demais;
- os valores precisam ser ajustaveis sem editar codigo.

## 5. Resilience4j no payment provider

**Status:** implementado

**Decisao**

Usar Resilience4j no provider de deposito em vez de depender de configuracao de retry incompleta ou annotations sem AOP efetivo.

**Como isso aparece no codigo**

- `StripePaymentProvider.createDeposit(...)` usa `@Retry` e `@CircuitBreaker`.
- o fallback comum levanta `PaymentProviderException` com mensagem de indisponibilidade temporaria.
- `application.yaml` concentra a configuracao de retry e circuit breaker da instancia `paymentProvider`.
- `PaymentProviderResilienceTest` cobre tentativas, excecoes ignoradas e transicoes do circuit breaker.

**Motivo**

- retry com backoff exponencial para falhas transitorias do Stripe;
- circuit breaker para reduzir cascata de chamadas quando o provider esta instavel;
- comportamento explicito e testado para excecoes de negocio que nao devem ser repetidas.

## 6. Ownership enforcement no nivel web

**Status:** implementado

**Decisao**

Validar ownership no controller e em servicos de borda usando o usuario autenticado, em vez de duplicar filtros de seguranca em cada consulta.

**Como isso aparece no codigo**

- `SecurityUtils.requireOwnership(...)` e chamado em controllers de user, wallet, transfer e transaction.
- `SecurityConfig` restringe o restante das rotas para usuarios autenticados.

**Motivo**

- regra de autorizacao fica centralizada;
- reduz duplicacao entre modulos;
- deixa claro quando uma rota depende de owner ou de papel administrativo.

## 7. Observabilidade com Micrometer, Prometheus e Grafana

**Status:** implementado

**Decisao**

Expor metricas de negocio e operacionais via Actuator/Micrometer e provisionar dashboards junto com a stack local.

**Como isso aparece no codigo**

- `PaymentMetrics` publica contadores e timers para transferencias, usuarios e outbox.
- `docker-compose.yml` sobe `prometheus` e `grafana`.
- `grafana/provisioning` e `grafana/dashboards` versionam datasource e dashboards.

**Motivo**

- a equipe precisa inspecionar latencia do outbox, volume de transferencias e falhas por motivo sem setup manual.

## Tradeoffs e limites atuais

- O unico payment provider implementado hoje e `STRIPE`.
- O CI ainda executa apenas `mvn test -B`; a suite de integracao do Failsafe nao roda no GitHub Actions.
- `logback-spring.xml` ainda usa padrao textual simples; logging JSON nao esta ativo no codigo atual.
- `AesEncryptor` ainda merece revisao para charset explicito e possivel migracao de CBC para GCM.
