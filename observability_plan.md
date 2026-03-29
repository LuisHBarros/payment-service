# Plano de Observabilidade — payment-service

> Métricas customizadas com Micrometer + Prometheus + Grafana, seed de dados e geração de carga.

---

## 1. Dependências (`pom.xml`)

```xml
<!-- Actuator (expõe /actuator/health, /actuator/metrics, etc.) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer → formato Prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

---

## 2. Configuração (`application.yaml`)

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

outbox:
  poll-interval: 500
  kafka-ack-timeout-ms: 5000
  max-attempts: 10
  cleanup:
    retention-days: 7
```

---

## 3. Métricas de domínio (`shared/metrics/PaymentMetrics.java`)

```java
@Component
@RequiredArgsConstructor
public class PaymentMetrics {

    private final MeterRegistry registry;

    // --- Transfers ---
    public void recordTransferCreated(BigDecimal amount) {
        registry.counter("payment.transfers.created.total").increment();
        registry.summary("payment.transfers.amount").record(amount.doubleValue());
    }

    public void recordTransferCompleted() {
        registry.counter("payment.transfers.completed.total").increment();
    }

    public void recordTransferFailed(String reason) {
        registry.counter("payment.transfers.failed.total",
            "reason", reason   // ex: "insufficient_balance", "wallet_not_found"
        ).increment();
    }

    // --- Outbox ---
    public void recordOutboxPublished(String eventType) {
        registry.counter("payment.outbox.published.total",
            "event_type", eventType
        ).increment();
    }

    public void recordOutboxFailed(String eventType) {
        registry.counter("payment.outbox.failed.total",
            "event_type", eventType
        ).increment();
    }

    public Timer outboxLatencyTimer() {
        return registry.timer("payment.outbox.latency");
    }

    // --- Users ---
    public void recordUserCreated(String userType) {
        registry.counter("payment.users.created.total",
            "type", userType   // "COMMON" ou "MERCHANT"
        ).increment();
    }
}
```

---

## 4. Instrumentação nos services

### `CreateTransferService.java`

```java
@Transactional
public UUID execute(UUID sourceWalletId, UUID destinationWalletId, BigDecimal amount) {
    // ... lógica existente

    metrics.recordTransferCreated(amount);
    return transfer.getId();
}
```

### `TransferStatusUpdateService.java`

```java
@Transactional
public void execute(UUID transferId, TransferStatus status) {
    // ... lógica existente

    if (status == TransferStatus.COMPLETED) {
        metrics.recordTransferCompleted();
    } else if (status == TransferStatus.FAILED) {
        metrics.recordTransferFailed("processing_error");
    }
}
```

### `OutboxPublisher.java`

```java
@Scheduled(fixedDelayString = "${outbox.poll-interval:500}")
public void publishPendingEvents() {
    List<OutboxEntity> entries = outboxRepository.findNextBatchForProcessing();

    for (OutboxEntity entry : entries) {
        Timer.Sample sample = Timer.start(registry);
        try {
            dispatch(entry);
            entry.setProcessed(true);
            entry.setProcessedAt(LocalDateTime.now());
            outboxRepository.save(entry);

            sample.stop(metrics.outboxLatencyTimer());
            metrics.recordOutboxPublished(entry.getEventType());

        } catch (Exception e) {
            metrics.recordOutboxFailed(entry.getEventType());
            // ... lógica de retry existente
        }
    }
}
```

### `CreateUserService.java`

```java
metrics.recordUserCreated(user.getType().name());
```

---

## 5. Docker Compose — Prometheus + Grafana

Adicionar ao `docker-compose.yml`:

```yaml
prometheus:
  image: prom/prometheus:latest
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml
  ports:
    - "9090:9090"

grafana:
  image: grafana/grafana:latest
  ports:
    - "3000:3000"
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=admin
  depends_on:
    - prometheus
```

### `prometheus.yml` (raiz do projeto)

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: payment-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["payment-service:8080"]
```

---

## 6. `seed.sh` — popular o banco via API

```bash
#!/bin/bash

set -e

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()    { echo -e "${BLUE}[SEED]${NC} $1"; }
ok()     { echo -e "${GREEN}[OK]${NC} $1"; }
warn()   { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()  { echo -e "${RED}[ERROR]${NC} $1"; }

wait_for_app() {
  log "Aguardando aplicação subir..."
  for i in $(seq 1 30); do
    if curl -sf "$BASE_URL/actuator/health" | grep -q '"status":"UP"'; then
      ok "Aplicação pronta."
      return 0
    fi
    sleep 2
    echo -n "."
  done
  error "Aplicação não respondeu em 60s. Abortando."
  exit 1
}

create_user() {
  local name=$1 email=$2 password=$3 document=$4
  local response
  response=$(curl -sf -X POST "$BASE_URL/api/v1/users" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$name\",\"email\":\"$email\",\"password\":\"$password\",\"document\":\"$document\"}")
  echo "$response" | tr -d '"'
}

login() {
  local identifier=$1 password=$2
  curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"identifier\":\"$identifier\",\"password\":\"$password\"}" \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4
}

get_wallet() {
  local userId=$1 token=$2
  curl -sf "$BASE_URL/api/v1/wallets/$userId" \
    -H "Authorization: Bearer $token"
}

create_transfer() {
  local sourceWalletId=$1 destWalletId=$2 amount=$3 token=$4
  curl -sf -X POST "$BASE_URL/api/v1/transfers" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "{\"sourceWalletId\":\"$sourceWalletId\",\"destinationWalletId\":\"$destWalletId\",\"amount\":$amount}" \
  | grep -o '"id":"[^"]*"' | cut -d'"' -f4
}

add_balance() {
  local userId=$1 amount=$2
  docker compose exec -T postgres psql -U payment_user -d payment_db -c \
    "UPDATE wallets SET balance = balance + $amount WHERE user_id = '$userId';" > /dev/null
}

main() {
  wait_for_app

  echo ""
  log "━━━ Criando usuários ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  ALICE_ID=$(create_user "Alice Souza"  "alice@seed.com" "Senha123!" "529.982.247-25")
  BOB_ID=$(create_user   "Bob Lima"     "bob@seed.com"   "Senha123!" "871.915.890-44")
  CAROL_ID=$(create_user "Carol Mendes" "carol@seed.com" "Senha123!" "674.385.490-07")
  SHOP_ID=$(create_user  "Loja Exemplo" "loja@seed.com"  "Senha123!" "11.222.333/0001-81")
  CAFE_ID=$(create_user  "Café Digital" "cafe@seed.com"  "Senha123!" "22.333.444/0001-05")

  ok "Alice   → $ALICE_ID"
  ok "Bob     → $BOB_ID"
  ok "Carol   → $CAROL_ID"
  ok "Loja    → $SHOP_ID"
  ok "Café    → $CAFE_ID"

  log "Aguardando Kafka criar carteiras..."
  sleep 5

  echo ""
  log "━━━ Obtendo tokens ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  ALICE_TOKEN=$(login "alice@seed.com" "Senha123!")
  BOB_TOKEN=$(login   "bob@seed.com"   "Senha123!")
  CAROL_TOKEN=$(login "carol@seed.com" "Senha123!")

  ok "Tokens obtidos."

  echo ""
  log "━━━ Injetando saldo inicial (via banco) ━━━━━━━━━━━━━━━"

  add_balance "$ALICE_ID" 1000.00
  add_balance "$BOB_ID"   500.00
  add_balance "$CAROL_ID" 250.00

  ok "Alice  → R\$ 1000,00"
  ok "Bob    → R\$ 500,00"
  ok "Carol  → R\$ 250,00"

  echo ""
  log "━━━ Obtendo IDs das carteiras ━━━━━━━━━━━━━━━━━━━━━━━━━"

  ALICE_WALLET=$(get_wallet "$ALICE_ID" "$ALICE_TOKEN" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
  BOB_WALLET=$(get_wallet   "$BOB_ID"   "$BOB_TOKEN"   | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
  CAROL_WALLET=$(get_wallet "$CAROL_ID" "$CAROL_TOKEN" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)

  SHOP_WALLET=$(docker compose exec -T postgres psql -U payment_user -d payment_db -tAc \
    "SELECT id FROM wallets WHERE user_id = '$SHOP_ID';")
  CAFE_WALLET=$(docker compose exec -T postgres psql -U payment_user -d payment_db -tAc \
    "SELECT id FROM wallets WHERE user_id = '$CAFE_ID';")

  ok "Alice wallet  → $ALICE_WALLET"
  ok "Bob wallet    → $BOB_WALLET"
  ok "Carol wallet  → $CAROL_WALLET"
  ok "Loja wallet   → $SHOP_WALLET"
  ok "Café wallet   → $CAFE_WALLET"

  echo ""
  log "━━━ Executando transferências ━━━━━━━━━━━━━━━━━━━━━━━━━"

  T1=$(create_transfer "$ALICE_WALLET" "$SHOP_WALLET" 150.00 "$ALICE_TOKEN")
  ok "Alice → Loja  R\$150,00  (id: $T1)"
  sleep 1

  T2=$(create_transfer "$BOB_WALLET" "$CAFE_WALLET" 80.00 "$BOB_TOKEN")
  ok "Bob → Café    R\$80,00   (id: $T2)"
  sleep 1

  T3=$(create_transfer "$CAROL_WALLET" "$SHOP_WALLET" 50.00 "$CAROL_TOKEN")
  ok "Carol → Loja  R\$50,00   (id: $T3)"
  sleep 1

  T4=$(create_transfer "$ALICE_WALLET" "$CAFE_WALLET" 200.00 "$ALICE_TOKEN")
  ok "Alice → Café  R\$200,00  (id: $T4)"
  sleep 1

  warn "Tentando transferência com saldo insuficiente (falha esperada)..."
  FAIL_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/v1/transfers" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $BOB_TOKEN" \
    -d "{\"sourceWalletId\":\"$BOB_WALLET\",\"destinationWalletId\":\"$SHOP_WALLET\",\"amount\":9999.00}")
  ok "Recebido HTTP $FAIL_RESPONSE (esperado 4xx)"

  echo ""
  log "━━━ Aguardando processamento assíncrono ━━━━━━━━━━━━━━━"
  sleep 5

  echo ""
  log "━━━ Estado final do banco ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  docker compose exec -T postgres psql -U payment_user -d payment_db -c "
    SELECT u.name, u.type, w.balance
    FROM wallets w
    JOIN users u ON u.id = w.user_id
    ORDER BY u.name;
  "

  echo ""
  docker compose exec -T postgres psql -U payment_user -d payment_db -c "
    SELECT id, status, amount, created_at
    FROM transfers
    ORDER BY created_at;
  "

  echo ""
  ok "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  ok "Seed concluído."
  ok ""
  ok "Prometheus : http://localhost:9090"
  ok "Grafana    : http://localhost:3000  (admin/admin)"
  ok "Métricas   : http://localhost:8080/actuator/prometheus"
  ok "Health     : http://localhost:8080/actuator/health"
  ok "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

main "$@"
```

---

## 7. `load.sh` — gerar carga contínua

```bash
#!/bin/bash

# Uso: ./load.sh 60   (roda por 60 segundos)

BASE_URL="http://localhost:8080"
DURATION=${1:-60}
END=$((SECONDS + DURATION))

echo "Gerando carga por ${DURATION}s... (Ctrl+C para parar)"

ALICE_TOKEN=$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"identifier":"alice@seed.com","password":"Senha123!"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

BOB_TOKEN=$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"identifier":"bob@seed.com","password":"Senha123!"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

ALICE_ID=$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"identifier":"alice@seed.com","password":"Senha123!"}' \
  | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)

BOB_ID=$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"identifier":"bob@seed.com","password":"Senha123!"}' \
  | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)

ALICE_WALLET=$(curl -sf "$BASE_URL/api/v1/wallets/$ALICE_ID" \
  -H "Authorization: Bearer $ALICE_TOKEN" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)

BOB_WALLET=$(curl -sf "$BASE_URL/api/v1/wallets/$BOB_ID" \
  -H "Authorization: Bearer $BOB_TOKEN" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)

SHOP_WALLET=$(docker compose exec -T postgres psql -U payment_user -d payment_db -tAc \
  "SELECT w.id FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.email = 'loja@seed.com';")

COUNT=0
while [ $SECONDS -lt $END ]; do
  docker compose exec -T postgres psql -U payment_user -d payment_db -c \
    "UPDATE wallets SET balance = 500 WHERE user_id = '$ALICE_ID';" > /dev/null 2>&1
  docker compose exec -T postgres psql -U payment_user -d payment_db -c \
    "UPDATE wallets SET balance = 500 WHERE user_id = '$BOB_ID';" > /dev/null 2>&1

  AMOUNT=$(awk "BEGIN{printf \"%.2f\", 1 + ($RANDOM % 50)}")

  curl -sf -X POST "$BASE_URL/api/v1/transfers" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ALICE_TOKEN" \
    -d "{\"sourceWalletId\":\"$ALICE_WALLET\",\"destinationWalletId\":\"$SHOP_WALLET\",\"amount\":$AMOUNT}" \
    > /dev/null

  COUNT=$((COUNT + 1))
  echo -ne "\rTransferências enviadas: $COUNT"
  sleep 0.5
done

echo ""
echo "Carga finalizada. $COUNT transferências enviadas."
```

---

## 8. Como rodar

```bash
# Permissão
chmod +x seed.sh load.sh

# 1. Subir toda a infra
docker compose up -d

# 2. Popular o banco
./seed.sh

# 3. Gerar carga por 2 minutos
./load.sh 120
```

---

## 9. Queries Prometheus (`localhost:9090`)

### Métricas de domínio (negócio)

```promql
# Transferências criadas por segundo (últimos 5min)
rate(payment_transfers_created_total[5m])

# Transferências completadas por segundo
rate(payment_transfers_completed_total[5m])

# Transferências falhadas por segundo
rate(payment_transfers_failed_total[5m])

# Transferências falhadas por segundo, agrupadas por motivo
rate(payment_transfers_failed_total[5m]) by (reason)

# Usuários criados por segundo
rate(payment_users_created_total[5m])

# Total de usuários criados por tipo (COMMON / MERCHANT)
payment_users_created_total

# Eventos do Outbox publicados por segundo, agrupados por tipo
rate(payment_outbox_published_total[5m]) by (event_type)

# Falhas de publicação do Outbox por segundo, agrupadas por tipo
rate(payment_outbox_failed_total[5m]) by (event_type)

# Latência média do Outbox
rate(payment_outbox_latency_seconds_sum[5m]) / rate(payment_outbox_latency_seconds_count[5m])

# Volume total transferido (soma dos valores)
payment_transfers_amount_sum
```

### HTTP / API

```promql
# Requisições por segundo no endpoint de transferências
rate(http_server_requests_seconds_count{uri="/api/v1/transfers"}[5m])

# Latência P95 do endpoint de transferências
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/v1/transfers"}[5m]))

# Erros 5xx por segundo
rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# Requisições por segundo agrupadas por status HTTP
rate(http_server_requests_seconds_count[5m]) by (status)

# Total de requisições por endpoint
sum by (uri) (http_server_requests_seconds_count)
```

### Infraestrutura (JVM, banco, Kafka)

```promql
# Uso de heap da JVM
jvm_memory_used_bytes{area="heap"}

# Tempo médio de pausa do GC
rate(jvm_gc_pause_seconds_sum[5m]) / rate(jvm_gc_pause_seconds_count[5m])

# Uso do pool de conexões com o banco (ativas / máximo)
hikaricp_connections_active / hikaricp_connections_max

# Uso de CPU do processo
process_cpu_usage

# Taxa de envio de records pelo Kafka producer
rate(kafka_producer_record_send_total[5m])

# Erros do Kafka producer por segundo
rate(kafka_producer_record_error_total[5m])

# Total de eventos de log por nível
logback_events_total

# Uso de memória do Redis Lettuce (comandos por segundo)
rate(lettuce_command_completion_seconds_count[5m])
```

---

## 10. Logs úteis em tempo real

```bash
# Todos os logs da aplicação
docker compose logs -f payment-service

# Filtrar só eventos do Outbox
docker compose logs -f payment-service | grep "OUTBOX\|outbox"

# Filtrar transferências
docker compose logs -f payment-service | grep "transfer\|Transfer"

# Filtrar erros
docker compose logs -f payment-service | grep "ERROR\|WARN"

# Logs do Kafka consumer
docker compose logs -f payment-service | grep "Received\|Successfully processed"
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