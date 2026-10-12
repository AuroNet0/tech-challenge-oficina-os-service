# Saga Orchestrator

O OS Service possui uma saga persistente por ordem de servico em `saga_instances`. A abertura de OS grava, na mesma transacao PostgreSQL, a OS, o historico inicial, a saga e o comando `diagnostic.start.v1` na outbox.

## Estados

Status da saga: `STARTED`, `WAITING_DIAGNOSTIC`, `WAITING_QUOTE`, `WAITING_APPROVAL`, `WAITING_REPAIR`, `WAITING_PAYMENT`, `COMPENSATING`, `COMPLETED`, `COMPENSATED`, `FAILED`.

Etapas: `DIAGNOSTIC`, `QUOTE`, `APPROVAL`, `REPAIR`, `PAYMENT`, `COMPENSATION`, `DONE`.

Status e etapa sao modelados separadamente. Regras de transicao nao usam `ordinal()`. Estados terminais (`COMPLETED`, `COMPENSATED`, `FAILED`) nao aceitam novas mudancas.

## Fluxo de Sucesso

1. Abertura da OS: saga `WAITING_DIAGNOSTIC`, comando `diagnostic.start.v1`.
2. `diagnostic.started.v1`: OS `RECEBIDA` para `EM_DIAGNOSTICO`; saga continua aguardando diagnostico.
3. `diagnostic.completed.v1`: valida itens e cria `quote.create.v1`; saga `WAITING_QUOTE`.
4. `quote.created.v1`: OS `AGUARDANDO_APROVACAO`; saga `WAITING_APPROVAL`.
5. `quote.approved.v1`: cria `repair.start.v1`; saga `WAITING_REPAIR`.
6. `repair.started.v1`: OS `EM_EXECUCAO`; saga continua aguardando reparo.
7. `repair.completed.v1`: OS `AGUARDANDO_PAGAMENTO`, cria `payment.request.v1`; saga `WAITING_PAYMENT`.
8. `payment.confirmed.v1`: OS `FINALIZADA`; saga `COMPLETED`.

A OS nao vira `ENTREGUE` automaticamente. Entrega segue como operacao administrativa futura.

## Falhas e Compensacoes

- `diagnostic.failed.v1` e `quote.creation-failed.v1`: antes de execucao fisica, a OS pode ser cancelada e a saga vira `COMPENSATED`.
- `quote.rejected.v1`: cancela a OS enquanto ainda nao houve execucao fisica e finaliza a saga como `COMPENSATED`.
- `repair.failed.v1`: registra intencao `repair.cancel.v1` na outbox e move a saga para `COMPENSATING`; nao ha rollback ficticio do trabalho fisico.
- `repair.cancelled.v1`: confirma a compensacao e conclui a saga como `COMPENSATED`. A OS so e cancelada se ainda estiver em estado cancelavel pelo dominio.
- `repair.cancellation-failed.v1`: conclui a saga como `FAILED`, pois a compensacao nao foi confirmada.
- `payment.failed.v1`: mantem a OS em `AGUARDANDO_PAGAMENTO`, mantem a saga em `WAITING_PAYMENT` e registra `lastError`. Uma confirmacao posterior `payment.confirmed.v1` ainda pode finalizar a saga.

## Inbox

`inbox_messages` usa `event_id` como chave primaria para deduplicacao. O processamento bloqueia o registro por `PESSIMISTIC_WRITE` quando existente e grava `PROCESSED` ou `REJECTED` na mesma transacao das alteracoes de OS, historico, saga e outbox.

Comportamentos:

- Mensagem duplicada: se `PROCESSED` ou `REJECTED`, e ignorada com ack.
- Evento atrasado: processa apenas se ainda for compativel com status e etapa atuais.
- Evento fora de ordem: vira `REJECTED`; nao e convertido em sucesso.
- Saga inexistente: vira `REJECTED`.
- Evento de outra OS: vira `REJECTED`.
- Concorrencia entre consumidores: a chave primaria do inbox e o lock pessimista impedem processamento simultaneo do mesmo evento ja registrado; a saga usa optimistic locking JPA.

## Outbox

Comandos subsequentes sao gravados em `outbox_events` dentro da mesma transacao do evento recebido. O publisher nao mantem transacao aberta enquanto aguarda confirmacao RabbitMQ. Apos publish confirmado, a atualizacao para `PUBLISHED` exige que o lease ainda corresponda ao evento adquirido.

## Limites Atuais

Nao existem Billing Service, Execution Service, integracao Mercado Pago nem consumidores reais para todos os comandos. Contratos compensatorios, como `repair.cancel.v1`, ficam documentados e persistidos na outbox; a conclusao depende de `repair.cancelled.v1` ou `repair.cancellation-failed.v1`.

## Testes

Execute:

```bash
./mvnw clean verify
```

Os testes de integracao usam Testcontainers com PostgreSQL e RabbitMQ. Para simular respostas dos servicos ainda nao implementados, publique envelopes em `oficina.events` com routing key igual ao `eventType` documentado em `docs/events.md`.
