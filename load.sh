#!/bin/bash

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log()    { echo -e "${BLUE}[LOAD]${NC} $1"; }
ok()     { echo -e "${GREEN}[OK]${NC} $1"; }
warn()   { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()  { echo -e "${RED}[ERROR]${NC} $1"; }
info()   { echo -e "${CYAN}[INFO]${NC} $1"; }

show_usage() {
  echo ""
  echo "Uso: ./load.sh [OPÇÕES]"
  echo ""
  echo "Opções:"
  echo "  -d, --duration SECONDS   Duração do teste em segundos (padrão: 60)"
  echo "  -c, --concurrency NUM    Número de requisições paralelas (padrão: 5)"
  echo "  -m, --mode MODE          Modo de teste: transfers, mixed, health (padrão: transfers)"
  echo "  -r, --rps NUM            Requisições por segundo alvo (padrão: 20)"
  echo "  -h, --help               Mostra esta ajuda"
  echo ""
  echo "Exemplos:"
  echo "  ./load.sh                          # Teste padrão de 60s"
  echo "  ./load.sh -d 120 -c 10             # 120s com 10 paralelas"
  echo "  ./load.sh -m mixed -d 30           # Modo misto por 30s"
  echo "  ./load.sh -m health -d 300         # Health check por 5min"
  echo ""
}

DURATION=60
CONCURRENCY=5
MODE="transfers"
TARGET_RPS=20

while [[ $# -gt 0 ]]; do
  case $1 in
    -d|--duration)
      DURATION="$2"
      shift 2
      ;;
    -c|--concurrency)
      CONCURRENCY="$2"
      shift 2
      ;;
    -m|--mode)
      MODE="$2"
      shift 2
      ;;
    -r|--rps)
      TARGET_RPS="$2"
      shift 2
      ;;
    -h|--help)
      show_usage
      exit 0
      ;;
    *)
      error "Opção desconhecida: $1"
      show_usage
      exit 1
      ;;
  esac
done

get_db_id() {
  docker compose exec -T postgres psql -U payment_user -d payment_db -tAc "$1" | tr -d '[:space:]'
}

# Helper function to make POST requests with JSON body using temp file
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

login() {
  local identifier=$1 password=$2
  local json_payload response
  json_payload=$(printf '{"identifier":"%s","password":"%s"}' "$identifier" "$password")
  response=$(post_json "$BASE_URL/api/v1/auth/login" "$json_payload")
  echo "$response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4 | tr -d '\r'
}

check_app() {
  curl -sf "$BASE_URL/actuator/health" | grep -q '"status":"UP"'
}

SUCCESS_COUNT=0
FAIL_COUNT=0
TOTAL_LATENCY=0

# Synchronous transfer - simpler and more reliable on Windows
do_transfer_sync() {
  local source_wallet=$1 dest_wallet=$2 token=$3
  local amount=$(printf "%d.%02d" $((1 + RANDOM % 50)) $((RANDOM % 100)))
  local json_payload tmpfile http_code
  json_payload=$(printf '{"sourceWalletId":"%s","destinationWalletId":"%s","amount":%s}' "$source_wallet" "$dest_wallet" "$amount")
  tmpfile=$(mktemp)
  printf '%s' "$json_payload" > "$tmpfile"
  
  http_code=$(curl -sf -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/v1/transfers" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d @"$tmpfile" 2>/dev/null)
  
  rm -f "$tmpfile"
  echo "$http_code"
}

do_health_check_sync() {
  curl -sf -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null
}

run_load_test() {
  local end_time=$((SECONDS + DURATION))
  local request_count=0
  local last_report=$SECONDS
  local start_seconds=$SECONDS
  
  echo ""
  log "Iniciando teste de carga..."
  info "Modo: $MODE | Duração: ${DURATION}s | Concorrência: $CONCURRENCY | RPS alvo: $TARGET_RPS"
  echo ""
  
  while [ $SECONDS -lt $end_time ]; do
    # Run requests synchronously for reliable counting
    for ((i=0; i<CONCURRENCY; i++)); do
      local http_code
      case $MODE in
        transfers)
          http_code=$(do_transfer_sync "$ALICE_WALLET" "$SHOP_WALLET" "$ALICE_TOKEN")
          ;;
        health)
          http_code=$(do_health_check_sync)
          ;;
        mixed)
          if [ $((RANDOM % 2)) -eq 0 ]; then
            http_code=$(do_transfer_sync "$ALICE_WALLET" "$SHOP_WALLET" "$ALICE_TOKEN")
          else
            http_code=$(do_health_check_sync)
          fi
          ;;
      esac
      
      ((request_count++))
      if [ "$http_code" = "200" ]; then
        ((SUCCESS_COUNT++))
      else
        ((FAIL_COUNT++))
      fi
    done
    
    if [ $((SECONDS - last_report)) -ge 3 ]; then
      local elapsed=$((SECONDS - start_seconds))
      local current_rps=$((request_count / (elapsed > 0 ? elapsed : 1)))
      echo -ne "\r${CYAN}[PROGRESS]${NC} Tempo: ${elapsed}s/${DURATION}s | Requisições: $request_count | RPS: ~$current_rps | Sucesso: $SUCCESS_COUNT | Falha: $FAIL_COUNT    "
      last_report=$SECONDS
    fi
  done
  
  echo ""
}

show_results() {
  local total=$((SUCCESS_COUNT + FAIL_COUNT))
  local success_rate=0
  local avg_latency=0
  local actual_rps=0
  
  if [ $total -gt 0 ]; then
    success_rate=$((SUCCESS_COUNT * 100 / total))
  fi
  
  if [ $SUCCESS_COUNT -gt 0 ]; then
    avg_latency=$((TOTAL_LATENCY / SUCCESS_COUNT))
  fi
  
  actual_rps=$((total / DURATION))
  
  echo ""
  echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${CYAN}║                   RESULTADOS DO TESTE                      ║${NC}"
  echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
  echo ""
  info "Configuração:"
  echo "  Modo:           $MODE"
  echo "  Duração:        ${DURATION}s"
  echo "  Concorrência:   $CONCURRENCY"
  echo "  RPS alvo:       $TARGET_RPS"
  echo ""
  info "Métricas:"
  echo "  Total requisições:    $total"
  echo "  Sucesso:              $SUCCESS_COUNT"
  echo "  Falhas:               $FAIL_COUNT"
  echo "  Taxa de sucesso:      ${success_rate}%"
  echo "  RPS real:             ~$actual_rps"
  echo "  Latência média:       ${avg_latency}ms"
  echo ""
  
  if [ $success_rate -ge 95 ]; then
    ok "Teste passou! Taxa de sucesso >= 95%"
  elif [ $success_rate -ge 80 ]; then
    warn "Teste com alertas. Taxa de sucesso entre 80-95%"
  else
    error "Teste falhou! Taxa de sucesso < 80%"
  fi
  
  echo ""
  info "Estado do banco após teste:"
  docker compose exec -T postgres psql -U payment_user -d payment_db -c "
    SELECT 
      (SELECT COUNT(*) FROM transfers) as total_transfers,
      (SELECT COUNT(*) FROM transfers WHERE status = 'SUCCESS') as success_transfers,
      (SELECT SUM(balance) FROM wallets) as total_balance;
  "
}

main() {
  echo ""
  echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${CYAN}║           PAYMENT SERVICE - LOAD TEST SCRIPT               ║${NC}"
  echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
  echo ""

  log "Verificando aplicação..."
  if ! check_app; then
    error "Aplicação não está respondendo. Verifique se está rodando."
    exit 1
  fi
  ok "Aplicação online"

  log "Coletando identificadores do banco..."
  
  ALICE_ID=$(get_db_id "SELECT id FROM users WHERE email = 'alice@seed.com';")
  BOB_ID=$(get_db_id "SELECT id FROM users WHERE email = 'bob@seed.com';")
  SHOP_ID=$(get_db_id "SELECT id FROM users WHERE email = 'loja@seed.com';")

  if [ -z "$ALICE_ID" ] || [ -z "$BOB_ID" ] || [ -z "$SHOP_ID" ]; then
    error "Usuários não encontrados no banco. Execute ./seed.sh primeiro."
    exit 1
  fi

  ok "Usuários encontrados"
  info "Alice: $ALICE_ID"
  info "Bob:   $BOB_ID"
  info "Loja:  $SHOP_ID"

  log "Obtendo tokens de autenticação..."
  
  ALICE_TOKEN=$(login "alice@seed.com" "Senha123!")
  BOB_TOKEN=$(login "bob@seed.com" "Senha123!")

  if [ -z "$ALICE_TOKEN" ] || [ -z "$BOB_TOKEN" ]; then
    error "Falha ao obter tokens. Verifique as credenciais."
    exit 1
  fi
  ok "Tokens obtidos"

  log "Buscando carteiras..."
  
  ALICE_WALLET=$(get_db_id "SELECT id FROM wallets WHERE user_id = '$ALICE_ID';")
  BOB_WALLET=$(get_db_id "SELECT id FROM wallets WHERE user_id = '$BOB_ID';")
  SHOP_WALLET=$(get_db_id "SELECT id FROM wallets WHERE user_id = '$SHOP_ID';")

  ok "Carteiras encontradas"

  log "Preparando saldo para teste..."
  docker compose exec -T postgres psql -U payment_user -d payment_db -c \
    "UPDATE wallets SET balance = 1000000 WHERE user_id IN ('$ALICE_ID', '$BOB_ID');" > /dev/null
  ok "Saldo de R\$ 1.000.000 injetado para Alice e Bob"

  run_load_test
  show_results

  echo ""
  info "Dica: Execute ./seed.sh para resetar os dados de teste"
  echo ""
}

main "$@"