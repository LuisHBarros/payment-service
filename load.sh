#!/bin/bash

# Configurações iniciais
BASE_URL="http://localhost:8080"
DURATION=${1:-60}
END=$((SECONDS + DURATION))

# Função para buscar ID no banco e limpar caracteres invisíveis do Windows
get_db_id() {
  docker compose exec -T postgres psql -U payment_user -d payment_db -tAc "$1" | tr -d '[:space:]'
}

echo "--- [1/3] Coletando Identificadores do Banco ---"

ALICE_ID=$(get_db_id "SELECT id FROM users WHERE email = 'alice@seed.com';")
BOB_ID=$(get_db_id "SELECT id FROM users WHERE email = 'bob@seed.com';")
SHOP_ID=$(get_db_id "SELECT id FROM users WHERE email = 'loja@seed.com';")

# Validação de segurança
if [ -z "$ALICE_ID" ] || [ -z "$BOB_ID" ]; then
    echo "ERRO: Usuários não encontrados no banco. Rode o seed.sh primeiro."
    exit 1
fi

echo "Alice ID: $ALICE_ID"
echo "Bob ID:   $BOB_ID"

echo "--- [2/3] Preparando Ambiente de Teste ---"

# 1. Login para pegar o Token (Necessário para o Header de Autenticação)
ALICE_TOKEN=$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"identifier":"alice@seed.com","password":"Senha123!"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4 | tr -d '\r')

# 2. Busca IDs das Carteiras (necessários para o payload da transferência)
ALICE_WALLET=$(get_db_id "SELECT id FROM wallets WHERE user_id = '$ALICE_ID';")
SHOP_WALLET=$(get_db_id "SELECT id FROM wallets WHERE user_id = '$SHOP_ID';")

# 3. Reset de Saldo ÚNICO (Ganha performance removendo SQL do loop)
echo "Injetando saldo de R$ 100.000 para o teste..."
docker compose exec -T postgres psql -U payment_user -d payment_db -c \
  "UPDATE wallets SET balance = 100000 WHERE user_id IN ('$ALICE_ID', '$BOB_ID');" > /dev/null

echo "--- [3/3] Iniciando Carga (${DURATION}s) ---"

COUNT=0
while [ $SECONDS -lt $END ]; do
  # Gera valor aleatório (1.00 a 50.99) sem problemas de locale/ponto decimal
  AMOUNT=$(printf "%d.%02d" $((1 + RANDOM % 50)) $((RANDOM % 100)))

  # Dispara a transferência em background (&) para maior vazão
  curl -sf -X POST "$BASE_URL/api/v1/transfers" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ALICE_TOKEN" \
    -d "{\"sourceWalletId\":\"$ALICE_WALLET\",\"destinationWalletId\":\"$SHOP_WALLET\",\"amount\":$AMOUNT}" \
    > /dev/null &

  COUNT=$((COUNT + 1))
  echo -ne "\rTransferências enviadas: $COUNT"
  
  # Pequeno delay para não exaurir as threads locais instantaneamente
  sleep 0.05
done

wait # Aguarda os últimos processos de background terminarem
echo -e "\nCarga finalizada com sucesso. $COUNT requisições disparadas."