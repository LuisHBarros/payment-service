# Decisões Técnicas

Registro de decisões técnicas tomadas durante o desenvolvimento do payment-service, incluindo implementações concluídas e melhorias futuras.

---

## ✅ Decisões Implementadas

### 1. Comunicação entre Contextos via Kafka

**Status:** ✅ Implementado (2026)

**Contexto:**
Inicialmente, o projeto tinha chamadas diretas entre contextos (`User → Wallet`, `Transfer → Wallet`), violando o princípio de isolamento do DDD.

**Decisão:**
Substituir todas as chamadas diretas por eventos Kafka para garantir desacoplamento completo entre contextos de domínio.

**Implementação:**
- `CreateUserService` publica `UserCreatedEvent` no tópico `payment.users`
- `CreateWalletConsumer` consome `UserCreatedEvent` e cria a carteira
- `CreateTransferService` publica `TransferCreatedEvent` no tópico `payment.transfers`
- `TransferWalletConsumer` consome `TransferCreatedEvent` e processa a transferência
- `ProcessTransferService` publica `WalletDebitedEvent` e `WalletCreditedEvent` no tópico `payment.wallets`

**Benefícios:**
- Desacoplamento total entre contextos
- Escalabilidade independente por contexto
- Resiliência via retry e DLT
- Auditoria via logs de eventos

---

### 2. Lock Pessimista Determinístico

**Status:** ✅ Implementado (2026)

**Contexto:**
Com a migração para eventos assíncronos, era necessário evitar race conditions e deadlocks em transferências concorrentes.

**Decisão:**
Implementar lock pessimista com ordenação determinística para garantir exclusão mútua e prevenir deadlocks.

**Implementação:**
```java
// WalletRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select w from WalletEntity w where w.id = :id")
Optional<WalletEntity> findByIdForUpdate(UUID id);
```

```java
// ProcessTransferService.java
// Sempre lock a carteira com menor UUID primeiro
UUID firstWalletId = sourceWalletId.compareTo(destinationWalletId) <= 0
    ? sourceWalletId
    : destinationWalletId;
```

**Benefícios:**
- Prevenção matemática de deadlocks (ordem total de locks)
- Proteção contra race conditions em saldos
- Permite concorrência em transferências não conflitantes
- Simplicidade do algoritmo (comparação de UUIDs)

---

### 3. Idempotência em Processamento de Transferências

**Status:** ✅ Implementado (2026)

**Contexto:**
Com retry e reprocessamento de mensagens Kafka, era essencial evitar processamento duplicado de transferências.

**Decisão:**
Implementar idempotência via `ProcessedTransferRepository` para garantir que cada transferência seja processada apenas uma vez.

**Implementação:**
```java
// ProcessTransferService.java
if (processedTransferRepository.existsById(transferId)) {
    log.info("Transfer {} already processed, skipping", transferId);
    return;
}
// ... processa transferência
var processedTransfer = new ProcessedTransferEntity();
processedTransfer.setId(transferId);
processedTransferRepository.save(processedTransfer);
```

**Benefícios:**
- Segurança contra reprocessamento duplicado
- Retries transparentes sem efeitos colaterais
- Garantia de exatamente-uma-vez em transferências bem-sucedidas

---

### 4. Idempotência em Atualizações de Status

**Status:** ✅ Implementado (2026)

**Contexto:**
Consumidores de `TransferStatusChangedEvent` poderiam receber eventos duplicados, causando atualizações redundantes.

**Decisão:**
Verificar status antes de atualizar `TransferEntity` para garantir idempotência.

**Implementação:**
```java
// TransferStatusConsumer.java
if (transfer.getStatus() != event.status()) {
    transfer.setStatus(event.status());
    transferRepository.save(transfer);
    log.info("Updated transfer status to {} for transferId={}",
             event.status(), event.transferId());
} else {
    log.info("Transfer status already {} for transferId={}, skipping update",
             event.status(), event.transferId());
}
```

**Benefícios:**
- Evita atualizações redundantes no banco
- Logs claros de quando updates são ignorados
- Suporte natural a reprocessamento de mensagens

---

### 5. Retry com Backoff Exponencial

**Status:** ✅ Implementado (2026)

**Contexto:**
Falhas transitórias (Kafka unavailable, timeout) poderiam causar falha de transferência permanentemente.

**Decisão:**
Implementar retry com backoff exponencial para lidar com falhas transitórias.

**Implementação:**
```java
// TransferPublishService.java
@Retryable(
    retryFor = {Exception.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public void publish(TransferCreatedEvent event) {
    kafkaEventProducer.publishTransferCreated(event);
}

@Recover
public void recover(Exception e, TransferCreatedEvent event) {
    transferStatusUpdateService.execute(event.transferId(), TransferStatus.FAILED);
}
```

**Benefícios:**
- Resiliência a falhas transitórias
- Delays progressivos para dar tempo de recuperação
- Fallback para status FAILED ao esgotar tentativas

---

### 6. Resilience4j para Payment Provider

**Status:** ✅ Implementado (2026)

**Contexto:**
`@EnableRetry` estava declarado na aplicação sem nenhum `@Retryable` em uso. Chamadas ao Stripe falhavam imediatamente sem retry, sem circuit breaker e sem fallback. Além disso, `spring-retry` estava no classpath sem `spring-boot-starter-aop`, tornando anotações inefetivas.

**Decisão:**
Migrar de `spring-retry` para Resilience4j como framework unificado de resiliência para o payment provider.

**Implementação:**
```java
// StripePaymentProvider.java
@Override
@Retry(name = "paymentProvider", fallbackMethod = "createDepositFallback")
@CircuitBreaker(name = "paymentProvider", fallbackMethod = "createDepositFallback")
public PaymentProviderResponse createDeposit(BigDecimal amount, UUID userId, UUID walletId) {
    // ...
}

private PaymentProviderResponse createDepositFallback(
        BigDecimal amount, UUID userId, UUID walletId, Exception e) {
    throw new PaymentProviderException("Payment provider temporarily unavailable.", e);
}
```

**Configuração (application.yaml):**
- Retry: 3 tentativas, backoff exponencial 500ms (x2), retry apenas em `PaymentProviderException`
- Circuit Breaker: abre com 50% falha em janela de 10 chamadas, slow call threshold 3s, wait 30s em open state
- Exceções de negócio (`WebhookSignatureException`, `InvalidPaymentProviderException`) são ignoradas pelo retry

**Benefícios:**
- Retry com backoff exponencial para falhas transitórias do Stripe
- Circuit breaker impede cascata de chamadas quando Stripe está indisponível
- Fallback method com mensagem clara ao cliente (HTTP 502)
- Métricas integradas com actuator/health via Resilience4j
- Eliminação de `spring-retry` órfão (sem AOP nunca funcionaria)

---

### 7. Arquitetura Híbrida: Spring Events + Kafka

**Status:** ✅ Implementado (2026)

**Contexto:**
Precisava garantir que eventos fossem publicados apenas após commit da transação, mas também wanted comunicação assíncrona entre contextos.

**Decisão:**
Usar Spring Events para comunicação síncrona intra-contexto (após commit) e Kafka para comunicação assíncrona inter-contexto.

**Implementação:**
```java
// CreateTransferService.java
@Transactional
public UUID execute(...) {
    // ... cria transferência
    eventPublisher.publishEvent(new TransferCreatedEvent(...)); // Spring Event
}

// TransferCreatedListener.java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handle(TransferCreatedEvent event) {
    transferPublishService.publish(event); // Kafka
}
```

**Benefícios:**
- Eventos publicados apenas após commit consistente
- Desacoplamento entre contextos
- Flexibilidade para diferentes padrões de comunicação
- Garantia de atomicidade na publicação de eventos locais

---

### 7. Remoção de Serviços Diretos de Wallet

**Status:** ✅ Implementado (2026)

**Contexto:**
`CreditWalletService` e `DebitWalletService` eram chamados diretamente pelo contexto `Transfer`, criando acoplamento forte.

**Decisão:**
Remover serviços diretos e substituir por fluxo baseado em eventos via `ProcessTransferService`.

**Implementação:**
- Removido: `CreditWalletService.java`, `DebitWalletService.java`
- Adicionado: `ProcessTransferService.java` (processa débito e crédito atômicos)
- Comunicação: `TransferCreatedEvent` → `TransferWalletConsumer` → `ProcessTransferService`

**Benefícios:**
- Desacoplamento completo entre `Transfer` e `Wallet`
- Processamento atômico de débito e crédito
- Simplificação do fluxo (um serviço para ambos)
- Publicação consistente de eventos

---

### 8. Dead Letter Topics (DLT)

**Status:** ✅ Implementado (2026)

**Contexto:**
Mensagens com falha permanente bloqueavam o consumo do tópico principal.

**Decisão:**
Configurar DLT para cada tópico Kafka, permitindo que mensagens com falha sejam isoladas sem bloquear o fluxo principal.

**Implementação:**
- `payment.users.DLT` para falhas em `payment.users`
- `payment.wallets.DLT` para falhas em `payment.wallets`
- `payment.transfers.DLT` para falhas em `payment.transfers`
- Configuração via `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`

**Benefícios:**
- Isolamento de mensagens com falha
- Possibilidade de reprocessamento manual
- Monitoramento de erros via DLT
- Continuidade do fluxo principal

---

### 9. Idempotência em Transações

**Status:** ✅ Implementado (2026)

**Contexto:**
O `TransactionConsumer` cria `TransactionEntity` baseado em eventos de wallet, mas não verificava se a transação já existia, podendo causar duplicação em caso de reprocessamento de mensagens.

**Decisão:**
Implementar verificação de unicidade antes de criar transação para garantir idempotência no ledger de transações.

**Implementação:**
```java
// CreateTransactionService.java
private void save(UUID walletID, UUID transferID, TransactionType type, BigDecimal amount) {
    // Idempotência: verificar se transação já existe
    boolean transactionExists = transactionRepository
        .existsByWalletIdAndTransferIdAndType(walletID, transferID, type);

    if (transactionExists) {
        log.info("Transaction already exists for walletId={}, transferId={}, type={}, skipping creation",
                 walletID, transferID, type);
        return;
    }

    TransactionEntity transaction = new TransactionEntity();
    transaction.setWalletId(walletID);
    transaction.setTransferId(transferID);
    transaction.setType(type);
    transaction.setAmount(amount);
    transactionRepository.save(transaction);
    log.info("Created transaction for walletId={}, transferId={}, type={}",
             walletID, transferID, type);
}
```

```java
// TransactionRepository.java
boolean existsByWalletIdAndTransferIdAndType(UUID walletId, UUID transferId, TransactionType type);
```

**Benefícios:**
- Prevenção de transações duplicadas no ledger
- Segurança contra reprocessamento de mensagens Kafka
- Consistência imutável do histórico financeiro
- Alinhado com idempotência implementada em outros contextos

---

## ⏳ Decisões Pendentes

### 1. Filtros Adicionais em `GET /transfers`

**Status:** ⏸️ Pendente

**Contexto:**
O endpoint `GET /api/v1/transfers?walletId=` foi implementado com paginação por `createdAt` descendente, mas sem filtros adicionais.

**Decisão pendente:**
Avaliar e implementar filtros adicionais baseados em requisitos de negócio.

**Possíveis filtros:**
- Por período (`startDate`, `endDate`)
- Por status (`COMPLETED`, `FAILED`, `PENDING`)
- Por tipo (`DEBIT`, `CREDIT`)

**Considerações:**
- Requisitos dependem de casos de uso reais
- Índices adicionais podem ser necessários no banco
- Performance deve ser considerada para tabelas grandes

---

### 2. Endpoints de Consulta ao Ledger de Transações

**Status:** ⏸️ Pendente

**Contexto:**
O módulo `transaction` persiste o ledger de movimentações, mas não expõe endpoints de consulta.

**Decisão pendente:**
Implementar endpoints para consulta do histórico de transações.

**Possíveis endpoints:**
- `GET /api/v1/transactions?walletId=` - Listar transações da carteira
- `GET /api/v1/transactions/{id}` - Detalhe de uma transação
- `GET /api/v1/transactions?transferId=` - Todas as transações de uma transferência

**Considerações:**
- Transações são imutáveis, então cache é viável
- Paginação é essencial (tabelas podem ser grandes)
- Filtros por período e tipo podem ser úteis

---

## 🔄 Decisões Consideradas (Não Implementadas)

### Lock Otimista em `WalletEntity`

**Status:** ❌ Descartado em favor de Lock Pessimista

**Contexto:**
Inicialmente foi considerado lock otimista via `@Version` para evitar conflitos.

**Motivo da descarte:**
- Lock pessimista determinístico foi implementado como solução mais robusta
- Garante exclusão mútua durante a transferência completa
- Simples e matematicamente correto (ordem total de locks)
- Lock otimista ainda pode ser adicionado como camada adicional se necessário

---

## 📊 Resumo de Evolução

| Mês | Mudança Principal | Impacto |
|---|---|---|
| 2026-03 | Implementação base com DDD | Contextos isolados, value objects |
| 2026-03 | Migração para Kafka | Desacoplamento completo entre contextos |
| 2026-03 | Lock pessimista + idempotência em transferências | Consistência em transferências concorrentes |
| 2026-03 | Retry + DLT | Resiliência em cenários de falha |
| 2026-03 | Idempotência em ledger de transações | Prevenção de duplicações no histórico financeiro |

---

## 🎯 Princípios Arquiteturais

As decisões tomadas seguem consistentemente os seguintes princípios:

1. **Desacoplamento:** Contextos comunicam-se apenas via eventos, sem dependências diretas
2. **Consistência:** Locks determinísticos e idempotência garantem integridade financeira
3. **Resiliência:** Retry, DLT e idempotência lidam com falhas gracefully
4. **Auditoria:** Ledger de transações imutável para traceabilidade completa
5. **Escalabilidade:** Arquitetura assíncrona permite escala independente por contexto
