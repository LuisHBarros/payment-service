# Decisões Pendentes

Registro de decisões técnicas identificadas durante o desenvolvimento, ainda não implementadas.

---

## 1. Migrar chamada direta `CreateUserService → CreateWalletService` para Kafka

### Contexto

`User` e `Wallet` são contextos distintos. Atualmente o `CreateUserService` chama o `CreateWalletService` diretamente, criando acoplamento entre contextos. A infraestrutura Kafka já está implementada na aplicação.

### Problema

Chamada direta entre contextos viola o princípio de isolamento do DDD. O contexto de `User` não deveria conhecer o contexto de `Wallet`.

### Decisão pendente

Substituir a chamada direta pela publicação do `UserCreatedEvent` no tópico `payment.users`. O `CreateWalletConsumer` já está implementado e pronto para consumir o evento.

### Mudança necessária

**`CreateUserService`** — substituir:
```java
// ❌ hoje
createWalletService.execute(user.getId());

// ✅ após a mudança
kafkaEventProducer.publishUserCreated(new UserCreatedEvent(user.getId()));
```

O `CreateWalletConsumer` já consome `payment.users` e chama `CreateWalletService.execute()` — nenhuma outra mudança necessária.

---

## 2. Migrar chamadas diretas de `DebitWalletService` e `CreditWalletService` para eventos

### Contexto

O `CreateTransferService` chama `DebitWalletService` e `CreditWalletService` diretamente, acoplando o contexto de `Transfer` ao de `Wallet`.

### Problema

Chamada direta entre contextos distintos viola o isolamento do DDD. Além disso, impede que o fluxo de transferência seja assíncrono.

### Decisão pendente

Substituir as chamadas diretas por eventos Kafka. O fluxo completo ficaria:

```
CreateTransferService
  → publica TransferStatusChangedEvent (PENDING)
  → publica WalletDebitedEvent
  → publica WalletCreditedEvent
    → TransactionConsumer persiste DEBIT e CREDIT
      → publica TransferStatusChangedEvent (COMPLETED ou FAILED)
        → TransferStatusConsumer atualiza status
```

### Observação

Ao migrar, garantir idempotência nos consumers — reprocessamento de `WalletDebitedEvent` ou `WalletCreditedEvent` não deve gerar movimentações duplicadas. Sugestão: verificar se já existe `TransactionEntity` com o mesmo `transferId` e `type` antes de persistir.

---

## 3. Lock otimista na `WalletEntity` após migração para eventos assíncronos

### Contexto

Atualmente `AuthorizationService` e `DebitWalletService` validam saldo suficiente em duas camadas — intencional para proteger contra race conditions quando os eventos forem assíncronos.

### Decisão pendente

Após migrar para eventos assíncronos (item 2), revisar se a validação dupla é suficiente ou se é necessário introduzir **lock otimista** na `WalletEntity`:

```java
@Version
private Long version;
```

O `@Version` do JPA garante que duas transações concorrentes não debitam o mesmo saldo sem conflito explícito.

---

## 4. Filtros adicionais em `GET /transfers`

### Contexto

O endpoint `GET /api/v1/transfers?walletId=` foi implementado com paginação por `createdAt` descendente.

### Decisão pendente

Avaliar a necessidade de filtros adicionais no futuro:
- Por período (`startDate`, `endDate`)
- Por status (`COMPLETED`, `FAILED`, `PENDING`)
- Por tipo (`DEBIT`, `CREDIT`)