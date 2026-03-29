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
  response=$(curl -s -X POST "$BASE_URL/api/v1/users" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$name\",\"email\":\"$email\",\"password\":\"$password\",\"document\":\"$document\"}") || \
    response=$(docker compose exec -T postgres psql -U payment_user -d payment_db -tAc "SELECT id FROM users WHERE email = '$email';")
  
  echo "$response" | tr -d '"' | tr -d '[:space:]'
}

login() {
  local identifier=$1 password=$2
  curl -s -X POST "$BASE_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"identifier\":\"$identifier\",\"password\":\"$password\"}" \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4
}

get_wallet() {
  local userId=$1 token=$2
  curl -s "$BASE_URL/api/v1/wallets/$userId" \
    -H "Authorization: Bearer $token"
}

create_transfer() {
  local sourceWalletId=$1 destWalletId=$2 amount=$3 token=$4
  curl -s -X POST "$BASE_URL/api/v1/transfers" \
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
  BOB_ID=$(create_user   "Bob Lima"     "bob@seed.com"   "Senha123!" "871.915.890-43")
  CAROL_ID=$(create_user "Carol Mendes" "carol@seed.com" "Senha123!" "674.385.490-54")
  SHOP_ID=$(create_user  "Loja Exemplo" "loja@seed.com"  "Senha123!" "11.222.333/0001-81")
  CAFE_ID=$(create_user  "Café Digital" "cafe@seed.com"  "Senha123!" "52.696.114/0001-11")

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