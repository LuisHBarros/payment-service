# payment-service

Documentação técnica · Versão 1.1 · 2026

---

## 1. Visão Geral

O **payment-service** é uma API REST desenvolvida em Java 21 com Spring Boot 3, responsável por gerenciar usuários, carteiras digitais e transferências financeiras entre pessoas físicas (COMMON) e lojistas (MERCHANT) no contexto do mercado brasileiro.

A aplicação segue os princípios de Clean Architecture e DDD, com separação clara entre contextos de domínio, uso de Value Objects para encapsular regras de domínio, comunicação entre contextos via eventos Kafka, e criptografia de dados sensíveis em repouso.

| | |
|---|---|
| **Linguagem** | Java 21 |
| **Framework** | Spring Boot 3 |
| **Banco de dados** | Relacional (JPA/Hibernate) |
| **Mensageria** | Apache Kafka |
| **Segurança** | AES-256-CBC com IV aleatório por registro |
| **Validação** | Bean Validation + Value Objects |
| **Build** | Maven |

---

## 2. Estrutura de Contextos

A aplicação é organizada em contextos de domínio independentes. A comunicação entre contextos é feita exclusivamente via eventos Kafka ou contratos do `shared`.

```
payment_service
├── shared          ← contratos, infraestrutura e utilitários transversais
├── user            ← cadastro e gestão de usuários
├── wallet          ← carteira digital e movimentações de saldo
├── transfer        ← orquestração de transferências
└── transaction     ← histórico imutável de movimentações
```

### Estrutura do `shared`

```
shared
├── config
│   ├── KafkaTopicsConfig
│   └── KafkaConsumerConfig
├── crypto
│   ├── AesEncryptor
│   └── HashUtil
├── dto
│   ├── UserSummary
│   └── WalletSummary
├── entity
│   └── BaseEntity
├── event
│   ├── UserCreatedEvent
│   ├── WalletDebitedEvent
│   ├── WalletCreditedEvent
│   └── TransferStatusChangedEvent
├── kafka
│   └── KafkaEventProducer
├── query
│   ├── UserQueryService
│   └── WalletQueryService
└── type
    └── TransferStatus
```

---

## 3. Módulo de Usuários

Gerencia o ciclo de vida dos usuários na plataforma. O tipo de usuário é derivado automaticamente do documento informado no cadastro.

### Tipos de usuário

| Tipo | Documento | Permissão |
|---|---|---|
| `COMMON` | CPF (11 dígitos) | Envia e recebe transferências |
| `MERCHANT` | CNPJ (14 dígitos) | Apenas recebe transferências |

### Campos da entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `name` | Nome completo ou razão social. Imutável após cadastro. |
| `email` | E-mail único. Validado por regex. |
| `password` | Hash BCrypt. Nunca armazenada em texto plano. |
| `document` | CPF ou CNPJ. Criptografado com AES-256-CBC. |
| `document_hash` | SHA-256 do documento normalizado. Garante unicidade no banco. |
| `type` | `COMMON` ou `MERCHANT`. Derivado automaticamente do documento. |
| `active` | Indica se o usuário está ativo. |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

### Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/users` | Cria usuário. Tipo derivado do documento. |
| `GET` | `/api/v1/users` | Lista usuários com documento mascarado. |
| `GET` | `/api/v1/users/{id}` | Retorna um usuário. |
| `PATCH` | `/api/v1/users/{id}` | Atualiza e-mail e/ou senha. |
| `DELETE` | `/api/v1/users/{id}` | Remove um usuário. |

### Evento publicado

| Evento | Tópico | Trigger |
|---|---|---|
| `UserCreatedEvent` | `payment.users` | Após persistência do usuário |

---

## 4. Módulo de Carteira (Wallet)

Cada usuário possui exatamente uma carteira, criada automaticamente ao consumir o `UserCreatedEvent`.

### Campos da entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `userId` | Referência ao usuário (1:1) |
| `balance` | Saldo em BRL. `BigDecimal`. Nunca negativo. |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

### Regras de negócio

- Saldo nunca pode ser negativo após débito.
- Carteira criada com saldo zero.
- Toda movimentação gera um `WalletDebitedEvent` ou `WalletCreditedEvent`.

### Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/v1/wallets/{userId}` | Retorna saldo e dados da carteira. |

### Eventos consumidos e publicados

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Consome | `UserCreatedEvent` | `payment.users` | Cria a carteira |
| Publica | `WalletDebitedEvent` | `payment.wallets` | Após débito |
| Publica | `WalletCreditedEvent` | `payment.wallets` | Após crédito |

---

## 5. Módulo de Transferência (Transfer)

Orquestra o fluxo completo de transferência entre usuários. Apenas `COMMON` pode enviar, apenas `MERCHANT` pode receber.

### Campos da entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `sourceWalletId` | Carteira do remetente |
| `destinationWalletId` | Carteira do destinatário |
| `amount` | Valor em BRL. `BigDecimal`. |
| `status` | `PENDING` → `COMPLETED` ou `FAILED` |
| `createdAt / updatedAt` | Gerenciados pela `BaseEntity`. |

### Fluxo de execução

| Etapa | Descrição |
|---|---|
| **1. Validação** | `AuthorizationService` verifica todas as regras antes de qualquer movimentação. |
| **2. Persistência** | `TransferEntity` criada com status `PENDING`. |
| **3. Débito** | `DebitWalletService` debita a carteira do remetente. |
| **4. Crédito** | `CreditWalletService` credita a carteira do destinatário. |
| **5. Conclusão** | Status atualizado para `COMPLETED` ou `FAILED`. |

### Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/transfers` | Inicia uma transferência. |
| `GET` | `/api/v1/transfers?walletId=` | Lista transferências da carteira (paginado, ordenado por `createdAt` DESC). |

### Eventos consumidos e publicados

| Direção | Evento | Tópico | Ação |
|---|---|---|---|
| Publica | `TransferStatusChangedEvent` | `payment.transfers` | Ao criar e ao concluir |
| Consome | `TransferStatusChangedEvent` | `payment.transfers` | Atualiza status da Transfer |

---

## 6. Módulo de Autorização (Authorization)

Serviço interno do contexto `transfer`. Valida todas as pré-condições antes de executar uma transferência. Consome apenas contratos de `shared` — sem acoplamento direto com `user` ou `wallet`.

### Regras de autorização

| Regra | Descrição |
|---|---|
| Remetente ativo | `sender.active() == true` |
| Destinatário ativo | `receiver.active() == true` |
| Saldo suficiente | `sourceWallet.balance() >= amount` |
| Tipo do remetente | `sender.canSend() == true` (COMMON) |
| Tipo do destinatário | `receiver.canReceive() == true` (MERCHANT) |
| Transferência para si mesmo | `sender.id() != receiver.id()` |

---

## 7. Módulo de Transações (Transaction)

Ledger imutável de todas as movimentações de saldo. Alimentado via Kafka — nunca chamado diretamente pelo fluxo de transferência.

### Campos da entidade

| Campo | Descrição |
|---|---|
| `id` | UUID gerado automaticamente |
| `walletId` | Carteira afetada |
| `transferId` | Referência à transferência origem |
| `type` | `DEBIT` ou `CREDIT` |
| `amount` | Valor em BRL. `BigDecimal`. |
| `createdAt` | Imutável. `setUpdatedAt` sobrescrito para no-op. |

Cada transferência bem-sucedida gera exatamente duas `Transaction`: `DEBIT` no remetente e `CREDIT` no destinatário.

### Eventos consumidos

| Evento | Tópico | Ação |
|---|---|---|
| `WalletDebitedEvent` | `payment.wallets` | Persiste `TransactionEntity` tipo `DEBIT` |
| `WalletCreditedEvent` | `payment.wallets` | Persiste `TransactionEntity` tipo `CREDIT` |

---

## 8. Kafka

### Tópicos

| Tópico | DLT | Produzido por | Consumido por |
|---|---|---|---|
| `payment.users` | `payment.users.DLT` | `CreateUserService` | `CreateWalletConsumer` |
| `payment.wallets` | `payment.wallets.DLT` | `DebitWalletService`, `CreditWalletService` | `TransactionConsumer` |
| `payment.transfers` | `payment.transfers.DLT` | `CreateTransferService` | `TransferStatusConsumer` |

### Configuração de erros

Cada consumer está configurado com `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`:
- 3 tentativas com intervalo de 1 segundo
- Após esgotar tentativas, mensagem enviada ao DLT correspondente

---

## 9. Decisões de Design

| Decisão | Justificativa |
|---|---|
| `UserType` derivado do documento | Elimina inconsistência entre tipo e documento. `CPF → COMMON`, `CNPJ → MERCHANT`. Imutável após cadastro. |
| `BigDecimal` para valores monetários | Evita erros de arredondamento em operações financeiras. |
| AES-256-CBC com IV aleatório | Mesmo CPF/CNPJ gera ciphertexts diferentes a cada persistência. |
| Hash SHA-256 para unicidade | Constraint `UNIQUE` no banco via hash determinístico, independente do ciphertext. |
| `Transaction` imutável | Auditabilidade completa. `setUpdatedAt` sobrescrito como no-op. |
| Wallet criada via evento | Desacopla `User` e `Wallet`. `CreateWalletConsumer` reage ao `UserCreatedEvent`. |
| `shared.query` com interfaces | `AuthorizationService` depende de interfaces, não de implementações de outros contextos. |
| `UserSummary` e `WalletSummary` em `shared` | Contratos de leitura transversais sem vazar entidades entre contextos. |
| `TransferStatus` em `shared.type` | Usado em eventos e em múltiplos contextos — sem dono único. |
| DLT por tópico | Mensagens com falha são isoladas sem bloquear o consumo do tópico principal. |