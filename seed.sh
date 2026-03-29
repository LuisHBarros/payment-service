#!/bin/bash

set -e

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log()    { echo -e "${BLUE}[SEED]${NC} $1"; }
ok()     { echo -e "${GREEN}[OK]${NC} $1"; }
warn()   { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()  { echo -e "${RED}[ERROR]${NC} $1"; }
info()   { echo -e "${CYAN}[INFO]${NC} $1"; }

section() {
  echo ""
  echo -e "${BLUE}━━━ $1 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

get_db_id() {
  docker compose exec -T postgres psql -U payment_user -d payment_db -tAc "$1" | tr -d '[:space:]'
}

# Helper function to make POST requests with JSON body
# Uses a temp file to avoid escaping issues on Windows
post_json() {
  local url=$1
  local json=$2
  local auth_header=$3
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s' "$json" > "$tmpfile"
  
  if [ -n "$auth_header" ]; then
    curl -s -X POST "$url" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $auth_header" \
      -d @"$tmpfile"
  else
    curl -s -X POST "$url" \
      -H "Content-Type: application/json" \
      -d @"$tmpfile"
  fi
  rm -f "$tmpfile"
}

# Helper function to make POST requests and get HTTP code
post_json_code() {
  local url=$1
  local json=$2
  local auth_header=$3
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s' "$json" > "$tmpfile"
  
  if [ -n "$auth_header" ]; then
    curl -s -o /dev/null -w "%{http_code}" -X POST "$url" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $auth_header" \
      -d @"$tmpfile"
  else
    curl -s -o /dev/null -w "%{http_code}" -X POST "$url" \
      -H "Content-Type: application/json" \
      -d @"$tmpfile"
  fi
  rm -f "$tmpfile"
}

# Helper function to make POST requests with JSON body and get both response and code
post_json_full() {
  local url=$1
  local json=$2
  local auth_header=$3
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s' "$json" > "$tmpfile"
  
  if [ -n "$auth_header" ]; then
    curl -s -w "\n%{http_code}" -X POST "$url" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $auth_header" \
      -d @"$tmpfile"
  else
    curl -s -w "\n%{http_code}" -X POST "$url" \
      -H "Content-Type: application/json" \
      -d @"$tmpfile"
  fi
  rm -f "$tmpfile"
}

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
  local http_code response body
  local json_payload
  json_payload=$(printf '{"name":"%s","email":"%s","password":"%s","document":"%s"}' "$name" "$email" "$password" "$document")
  
  response=$(post_json_full "$BASE_URL/api/v1/users" "$json_payload")
  
  http_code=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')
  
  if [ "$http_code" = "201" ]; then
    echo "$body" | tr -d '"' | tr -d '[:space:]'
  elif [ "$http_code" = "409" ]; then
    get_db_id "SELECT id FROM users WHERE email = '$email';"
  else
    error "Falha ao criar usuário $email (HTTP $http_code): $body"
    return 1
  fi
}

login() {
  local identifier=$1 password=$2
  local response token json_payload
  json_payload=$(printf '{"identifier":"%s","password":"%s"}' "$identifier" "$password")
  
  response=$(post_json "$BASE_URL/api/v1/auth/login" "$json_payload")
  
  token=$(echo "$response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4 | tr -d '\r')
  
  if [ -z "$token" ]; then
    error "Falha ao fazer login com $identifier"
    return 1
  fi
  
  echo "$token"
}

get_me() {
  local token=$1
  curl -s "$BASE_URL/api/v1/auth/me" \
    -H "Authorization: Bearer $token"
}

get_user() {
  local userId=$1 token=$2
  curl -s "$BASE_URL/api/v1/users/$userId" \
    -H "Authorization: Bearer $token"
}

get_wallet() {
  local userId=$1 token=$2
  curl -s "$BASE_URL/api/v1/wallets/$userId" \
    -H "Authorization: Bearer $token"
}

get_wallet_id() {
  local userId=$1 token=$2
  get_wallet "$userId" "$token" | grep -o '"id":"[^"]*"' | cut -d'"' -f4 | tr -d '\r'
}

get_wallet_balance() {
  local userId=$1 token=$2
  get_wallet "$userId" "$token" | grep -o '"balance":[0-9.]*' | cut -d':' -f2
}

create_transfer() {
  local sourceWalletId=$1 destWalletId=$2 amount=$3 token=$4
  local response http_code body json_payload
  json_payload=$(printf '{"sourceWalletId":"%s","destinationWalletId":"%s","amount":%s}' "$sourceWalletId" "$destWalletId" "$amount")
  
  response=$(post_json_full "$BASE_URL/api/v1/transfers" "$json_payload" "$token")
  
  http_code=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')
  
  if [ "$http_code" = "200" ]; then
    echo "$body" | grep -o '"id":"[^"]*"' | cut -d'"' -f4 | tr -d '\r'
  else
    echo "FAIL:$http_code"
  fi
}

get_transfers() {
  local walletId=$1 token=$2
  curl -s "$BASE_URL/api/v1/transfers?walletId=$walletId" \
    -H "Authorization: Bearer $token"
}

add_balance() {
  local userId=$1 amount=$2
  docker compose exec -T postgres psql -U payment_user -d payment_db -c \
    "UPDATE wallets SET balance = balance + $amount WHERE user_id = '$userId';" > /dev/null
}

test_auth_flow() {
  section "Testando fluxo de autenticação"
  
  local token=$(login "alice@seed.com" "Senha123!")
  if [ -z "$token" ]; then
    error "Login falhou"
    return 1
  fi
  ok "Login com email funcionou"
  
  local me_response=$(get_me "$token")
  if echo "$me_response" | grep -q '"email":"alice@seed.com"'; then
    ok "GET /auth/me retornou dados corretos"
  else
    warn "GET /auth/me resposta inesperada: $me_response"
  fi
  
  local doc_token=$(login "529.982.247-25" "Senha123!")
  if [ -n "$doc_token" ]; then
    ok "Login com documento funcionou"
  else
    warn "Login com documento falhou"
  fi
  
  local invalid_payload='{"identifier":"invalid@test.com","password":"wrong"}'
  local invalid_response=$(post_json_code "$BASE_URL/api/v1/auth/login" "$invalid_payload")
  if [ "$invalid_response" = "401" ]; then
    ok "Login inválido retornou 401"
  else
    warn "Login inválido retornou $invalid_response (esperado 401)"
  fi
}

test_user_flow() {
  section "Testando fluxo de usuários"
  
  local token=$(login "alice@seed.com" "Senha123!")
  local alice_id=$(get_db_id "SELECT id FROM users WHERE email = 'alice@seed.com';")
  
  local user_response=$(get_user "$alice_id" "$token")
  if echo "$user_response" | grep -q '"name":"Alice Souza"'; then
    ok "GET /users/{id} retornou dados corretos"
  else
    warn "GET /users/{id} resposta inesperada"
  fi
  
  local bob_id=$(get_db_id "SELECT id FROM users WHERE email = 'bob@seed.com';")
  local forbidden_response=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/users/$bob_id" \
    -H "Authorization: Bearer $token")
  if [ "$forbidden_response" = "403" ]; then
    ok "Acesso a outro usuário retornou 403"
  else
    warn "Acesso a outro usuário retornou $forbidden_response (esperado 403)"
  fi
}

test_wallet_flow() {
  section "Testando fluxo de carteiras"
  
  local alice_token=$(login "alice@seed.com" "Senha123!")
  local alice_id=$(get_db_id "SELECT id FROM users WHERE email = 'alice@seed.com';")
  
  local wallet_response=$(get_wallet "$alice_id" "$alice_token")
  if echo "$wallet_response" | grep -q '"id":"'; then
    ok "GET /wallets/{userId} retornou carteira"
  else
    warn "GET /wallets/{userId} resposta inesperada: $wallet_response"
  fi
  
  local balance=$(get_wallet_balance "$alice_id" "$alice_token")
  info "Saldo atual de Alice: R\$ $balance"
  
  local bob_id=$(get_db_id "SELECT id FROM users WHERE email = 'bob@seed.com';")
  local forbidden_response=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/wallets/$bob_id" \
    -H "Authorization: Bearer $alice_token")
  if [ "$forbidden_response" = "403" ]; then
    ok "Acesso a carteira de outro usuário retornou 403"
  else
    warn "Acesso a carteira de outro usuário retornou $forbidden_response (esperado 403)"
  fi
}

test_transfer_flow() {
  section "Testando fluxo de transferências"
  
  local alice_token=$(login "alice@seed.com" "Senha123!")
  local bob_token=$(login "bob@seed.com" "Senha123!")
  
  local alice_id=$(get_db_id "SELECT id FROM users WHERE email = 'alice@seed.com';")
  local bob_id=$(get_db_id "SELECT id FROM users WHERE email = 'bob@seed.com';")
  local shop_id=$(get_db_id "SELECT id FROM users WHERE email = 'loja@seed.com';")
  
  local alice_wallet=$(get_wallet_id "$alice_id" "$alice_token")
  local bob_wallet=$(get_wallet_id "$bob_id" "$bob_token")
  local shop_wallet=$(get_db_id "SELECT id FROM wallets WHERE user_id = '$shop_id';")
  
  local alice_balance_before=$(get_wallet_balance "$alice_id" "$alice_token")
  info "Saldo Alice antes: R\$ $alice_balance_before"
  
  local transfer_id=$(create_transfer "$alice_wallet" "$shop_wallet" 25.50 "$alice_token")
  if [[ "$transfer_id" != FAIL:* ]]; then
    ok "Transferência criada: $transfer_id"
    sleep 2
    
    local alice_balance_after=$(get_wallet_balance "$alice_id" "$alice_token")
    info "Saldo Alice depois: R\$ $alice_balance_after"
  else
    warn "Transferência falhou: $transfer_id"
  fi
  
  local transfers_response=$(get_transfers "$alice_wallet" "$alice_token")
  if echo "$transfers_response" | grep -q '"content":\['; then
    ok "GET /transfers?walletId retornou lista paginada"
  else
    warn "GET /transfers resposta inesperada"
  fi
  
  local fail_result=$(create_transfer "$alice_wallet" "$shop_wallet" 999999.00 "$alice_token")
  if [[ "$fail_result" == FAIL:* ]]; then
    ok "Transferência com saldo insuficiente falhou corretamente"
  else
    warn "Transferência com saldo insuficiente deveria ter falhado"
  fi
  
  local unauthorized_result=$(create_transfer "$bob_wallet" "$shop_wallet" 10.00 "$alice_token")
  if [[ "$unauthorized_result" == FAIL:403 ]]; then
    ok "Transferência de carteira alheia retornou 403"
  else
    warn "Transferência de carteira alheia: $unauthorized_result (esperado FAIL:403)"
  fi
}

test_merchant_restrictions() {
  section "Testando restrições de lojista"
  
  local shop_token=$(login "loja@seed.com" "Senha123!")
  local shop_id=$(get_db_id "SELECT id FROM users WHERE email = 'loja@seed.com';")
  local alice_id=$(get_db_id "SELECT id FROM users WHERE email = 'alice@seed.com';")
  
  local shop_wallet=$(get_db_id "SELECT id FROM wallets WHERE user_id = '$shop_id';")
  local alice_wallet=$(get_db_id "SELECT id FROM wallets WHERE user_id = '$alice_id';")
  
  add_balance "$shop_id" 100.00
  
  local merchant_payload=$(printf '{"sourceWalletId":"%s","destinationWalletId":"%s","amount":10.00}' "$shop_wallet" "$alice_wallet")
  local merchant_transfer=$(post_json_code "$BASE_URL/api/v1/transfers" "$merchant_payload" "$shop_token")
  
  if [ "$merchant_transfer" = "403" ]; then
    ok "Lojista não pode enviar transferências (403)"
  else
    warn "Lojista transferência retornou $merchant_transfer (esperado 403)"
  fi
}

show_final_state() {
  section "Estado final do banco"
  
  echo ""
  info "Usuários e saldos:"
  docker compose exec -T postgres psql -U payment_user -d payment_db -c "
    SELECT u.name, u.email, u.type, w.balance
    FROM wallets w
    JOIN users u ON u.id = w.user_id
    ORDER BY u.type, u.name;
  "
  
  echo ""
  info "Últimas transferências:"
  docker compose exec -T postgres psql -U payment_user -d payment_db -c "
    SELECT 
      t.id,
      t.status,
      t.amount,
      t.created_at
    FROM transfers t
    ORDER BY t.created_at DESC
    LIMIT 10;
  "
  
  echo ""
  info "Contadores:"
  local user_count=$(get_db_id "SELECT COUNT(*) FROM users;")
  local wallet_count=$(get_db_id "SELECT COUNT(*) FROM wallets;")
  local transfer_count=$(get_db_id "SELECT COUNT(*) FROM transfers;")
  echo "  Usuários: $user_count"
  echo "  Carteiras: $wallet_count"
  echo "  Transferências: $transfer_count"
}

main() {
  echo ""
  echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${CYAN}║           PAYMENT SERVICE - SEED & TEST SCRIPT            ║${NC}"
  echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
  echo ""

  wait_for_app

  section "Criando usuários"

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

  section "Injetando saldo inicial"

  add_balance "$ALICE_ID" 1000.00
  add_balance "$BOB_ID"   500.00
  add_balance "$CAROL_ID" 250.00

  ok "Alice  → R\$ 1000,00"
  ok "Bob    → R\$ 500,00"
  ok "Carol  → R\$ 250,00"

  section "Executando transferências iniciais"

  ALICE_TOKEN=$(login "alice@seed.com" "Senha123!")
  BOB_TOKEN=$(login "bob@seed.com" "Senha123!")
  CAROL_TOKEN=$(login "carol@seed.com" "Senha123!")

  ALICE_WALLET=$(get_wallet_id "$ALICE_ID" "$ALICE_TOKEN")
  BOB_WALLET=$(get_wallet_id "$BOB_ID" "$BOB_TOKEN")
  CAROL_WALLET=$(get_wallet_id "$CAROL_ID" "$CAROL_TOKEN")
  SHOP_WALLET=$(get_db_id "SELECT id FROM wallets WHERE user_id = '$SHOP_ID';")
  CAFE_WALLET=$(get_db_id "SELECT id FROM wallets WHERE user_id = '$CAFE_ID';")

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

  log "Aguardando processamento assíncrono..."
  sleep 3

  test_auth_flow
  test_user_flow
  test_wallet_flow
  test_transfer_flow
  test_merchant_restrictions

  show_final_state

  echo ""
  echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║                    SEED CONCLUÍDO                          ║${NC}"
  echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
  echo ""
  info "Endpoints disponíveis:"
  echo "  API        : http://localhost:8080"
  echo "  Swagger    : http://localhost:8080/swagger-ui.html"
  echo "  Health     : http://localhost:8080/actuator/health"
  echo "  Métricas   : http://localhost:8080/actuator/prometheus"
  echo "  Prometheus : http://localhost:9090"
  echo "  Grafana    : http://localhost:3000 (admin/admin)"
  echo "  Jaeger     : http://localhost:16686"
  echo ""
}

main "$@"