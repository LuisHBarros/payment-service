# Decisões Pendentes

Registro de decisões técnicas identificadas durante o desenvolvimento, ainda não implementadas.

---

## 1. Eventos entre contextos (User → Wallet)

### Contexto

`User` e `Wallet` estão organizados em packages separados mas rodam no mesmo processo. Atualmente, o `CreateUserService` chama o `CreateWalletService` diretamente, criando acoplamento entre contextos.

### Problema

Chamada direta entre contextos viola o princípio de isolamento do DDD. O contexto de `User` não deveria conhecer o contexto de `Wallet`.

### Decisão pendente

Introduzir eventos internos para desacoplar a comunicação. Quando um usuário é criado, um evento `UserCreatedEvent` deve ser publicado, e o `CreateWalletService` deve reagir a ele de forma independente.

### Opções

| Opção | Quando usar | Observação |
|---|---|---|
| **Spring Application Events** | Monólito, mesmo processo | Sem infraestrutura adicional. Recomendado para o estado atual da aplicação. |
| **Kafka** | Microserviços ou alto volume | Requer broker externo. Viável se a aplicação for eventualmente decomposta. |

### Recomendação

Implementar com **Spring Application Events** por ora:

```java
// Publicar no CreateUserService
applicationEventPublisher.publishEvent(new UserCreatedEvent(user.getId()));

// Consumir no CreateWalletService
@EventListener
public void onUserCreated(UserCreatedEvent event) {
    execute(event.userId());
}
```

A migração para Kafka no futuro é cirúrgica — apenas o mecanismo de transporte muda, a lógica de negócio permanece intacta.

---

## 2. Kafka para registro de Transactions

### Contexto

O módulo de `Transaction` funciona como ledger auditável de todas as movimentações de saldo. Toda transferência bem-sucedida gera duas transactions: `DEBIT` (remetente) e `CREDIT` (destinatário).

### Decisão pendente

Utilizar **Kafka** como mecanismo de transporte para o registro de transactions, desacoplando a persistência do fluxo principal de transferência.

### Motivação

- O registro de transaction é um efeito colateral da transferência — não deveria bloquear o fluxo principal.
- Kafka garante durabilidade e permite replay em caso de falha na persistência.
- Abre caminho para consumidores adicionais no futuro (ex: notificações, antifraude, relatórios).
- Aproveita a infraestrutura que já será necessária para o item 1 desta lista.

### Fluxo proposto

```
TransferService
  → publica TransactionEvent no tópico Kafka
    → TransactionConsumer consome e persiste TransactionEntity
```

### Tópicos sugeridos

| Tópico | Produzido por | Consumido por |
|---|---|---|
| `payment.transactions` | `TransferService` | `TransactionConsumer` |
| `payment.user-created` | `CreateUserService` | `CreateWalletConsumer` |

### Observação

Ao introduzir Kafka, considerar:
- Idempotência no consumer (reprocessamento não deve gerar transactions duplicadas)
- Dead Letter Topic (DLT) para mensagens que falham após N tentativas
- Schema para os eventos (record Java ou Avro)