# Observabilidade — payment-service

Última atualização: 2026-03-29

---

## Arquitetura

```text
Payment Service (Micrometer)
    │
    ├── /actuator/prometheus  ──→  Prometheus (scrape 30s)
    │                              │
    │                              └──→  Grafana (3 dashboards)
    │
    └── Logback (console)   ──→  docker compose logs -f
```

---

## 1. Stack

| Componente | Versão/Formato | Função |
|---|---|---|
| Micrometer + `micrometer-registry-prometheus` | Spring Boot managed | Coleta e exposição de métricas |
| Actuator | `health, metrics, prometheus, info` | Endpoints de operação |
| Prometheus | `prom/prometheus:latest` | Armazenamento TSDB e queries PromQL |
| Grafana | `grafana/grafana:latest` | Visualização (3 dashboards provisionados) |
| Logback | `logback-spring.xml` | Logging estruturado em console |

---

## 2. Configuração

### `application.yaml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus, info
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: payment-service
```

### `prometheus.yml`

- `scrape_interval: 30s`
- `evaluation_interval: 30s`
- `scrape_timeout: 10s`
- Job `payment-service` aponta para `payment-service:8080/actuator/prometheus`

### `docker-compose.yml`

- Prometheus e Grafana na rede `payment-network`
- Volumes persistentes para dados do Prometheus e Grafana
- Grafana com provisioning automático de datasource e dashboards

---

## 3. Métricas de domínio

### Registro (`shared/metrics/PaymentMetrics.java`)

| Métrica | Tipo | Labels | Descrição |
|---|---|---|---|
| `payment.transfers.created.total` | Counter | — | Total de transferências criadas |
| `payment.transfers.amount` | Summary | — | Distribuição dos valores transferidos |
| `payment.transfers.completed.total` | Counter | — | Total de transferências completadas |
| `payment.transfers.failed.total` | Counter | `reason` | Total de transferências falhadas por motivo |
| `payment.outbox.published.total` | Counter | `event_type` | Eventos do outbox publicados com sucesso |
| `payment.outbox.failed.total` | Counter | `event_type` | Falhas de publicação do outbox |
| `payment.outbox.latency` | Timer | — | Latência do ciclo de publicação do outbox |
| `payment.users.created.total` | Counter | `type` | Usuários criados (COMMON/MERCHANT) |

### Instrumentação por service

| Service | Métricas emitidas |
|---|---|
| `CreateTransferService` | `recordTransferCreated(amount)` |
| `TransferStatusUpdateService` | `recordTransferCompleted()` / `recordTransferFailed(reason)` |
| `OutboxPublisher` | `recordOutboxPublished(eventType)` / `recordOutboxFailed(eventType)` / `outboxLatencyTimer()` |
| `CreateUserService` | `recordUserCreated(userType)` |

### Métricas automáticas do Spring Boot

| Categoria | Exemplos |
|---|---|
| HTTP | `http_server_requests_seconds_*` (por URI, status, método) |
| JVM | `jvm_memory_used_bytes`, `jvm_gc_pause_seconds_*`, `process_cpu_usage` |
| Banco | `hikaricp_connections_active`, `hikaricp_connections_max` |
| Kafka | `kafka_producer_record_send_total`, `kafka_producer_record_error_total` |
| Redis | `lettuce_command_completion_seconds_count` |

---

## 4. Dashboards Grafana

Provisionamento automático via `grafana/provisioning/`.

| Dashboard | Arquivo | Conteúdo |
|---|---|---|
| Business Metrics | `business-metrics.json` | Transfers criadas/completadas/falhadas, volume transferido, usuários por tipo |
| Application API | `application-api.json` | Requisições/s por endpoint, latência P50/P95/P99, distribuição de status HTTP |
| Infrastructure JVM | `infrastructure-jvm.json` | Heap, GC, CPU, threads, conexões de banco |

Datasource Prometheus provisionado automaticamente em `grafana/provisioning/datasources/prometheus.yml`.

---

## 5. Logging

### Formato (`logback-spring.xml`)

```
yyyy-MM-dd HH:mm:ss.SSS [thread] LEVEL payment-service logger - message
```

Root level: `INFO`.

### Comandos úteis

```bash
docker compose logs -f payment-service
docker compose logs -f payment-service | grep "ERROR\|WARN"
docker compose logs -f payment-service | grep "Received\|Successfully processed"
docker compose logs -f payment-service | grep "OUTBOX\|outbox"
```

---

## 6. Queries PromQL de referência

### Negócio

```promql
rate(payment_transfers_created_total[5m])
rate(payment_transfers_completed_total[5m])
rate(payment_transfers_failed_total[5m]) by (reason)
rate(payment_users_created_total[5m]) by (type)
rate(payment_outbox_published_total[5m]) by (event_type)
rate(payment_outbox_failed_total[5m]) by (event_type)
rate(payment_outbox_latency_seconds_sum[5m]) / rate(payment_outbox_latency_seconds_count[5m])
payment_transfers_amount_sum
```

### API

```promql
rate(http_server_requests_seconds_count{uri="/api/v1/transfers"}[5m])
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/v1/transfers"}[5m]))
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
sum by (uri) (http_server_requests_seconds_count)
```

### Infraestrutura

```promql
jvm_memory_used_bytes{area="heap"}
rate(jvm_gc_pause_seconds_sum[5m]) / rate(jvm_gc_pause_seconds_count[5m])
hikaricp_connections_active / hikaricp_connections_max
process_cpu_usage
rate(kafka_producer_record_error_total[5m])
```

---

## 7. Ferramentas de carga

### `seed.sh` — popular dados via API

Cria 5 usuários (3 COMMON + 2 MERCHANT), injeta saldo via banco, executa 4 transferências e 1 transferência com saldo insuficiente. Possui retry de criação de usuário via fallback para query direta no banco.

```bash
chmod +x seed.sh
./seed.sh
```

### `load.sh` — gerar carga contínua

Busca IDs via banco, reset de saldo em batch, e dispara transferências em background (`&`) com `sleep 0.05s` para alta vazão.

```bash
chmod +x load.sh
./load.sh 120   # 120 segundos de carga
```

---

## 8. Como rodar

```bash
docker compose up -d
./seed.sh
./load.sh 120
```

---

## URLs

| Serviço | URL | Credenciais |
|---|---|---|
| Aplicação | http://localhost:8080 | — |
| Health | http://localhost:8080/actuator/health | — |
| Métricas raw | http://localhost:8080/actuator/prometheus | — |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin / admin |

---

## 9. Lacunas e melhorias futuras

| Item | Descrição |
|---|---|
| Métricas de deposit | Adicionar counters para depósitos criados, processados e falhados em `PaymentMetrics` |
| Métricas de rate limiting | Contar requests bloqueados por endpoint |
| Métricas de Kafka consumer | Contar mensagens processadas por topic, lag do consumer group |
| Logging estruturado JSON | Atualizar `logback-spring.xml` para output JSON (atualmente console text) |
| Alertas no Grafana | Definir regras de alerta (ex: transfer fail rate > threshold, DB connection pool exausto) |
| Distributed tracing | Integrar OpenTelemetry para rastreamento end-to-end de transfer → wallet → transaction |
