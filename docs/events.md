# Eventos e Comandos RabbitMQ

## Envelope

Todos os comandos e eventos usam:

```json
{
  "eventId": "uuid",
  "eventType": "diagnostic.completed.v1",
  "occurredAt": "2026-10-12T12:00:00Z",
  "correlationId": "uuid",
  "sagaId": "uuid",
  "osId": "uuid",
  "source": "service-name",
  "payload": {}
}
```

`eventId` e usado para idempotencia. `correlationId` e preservado para logs, historico e comandos derivados.

`occurredAt` e sempre uma string ISO-8601, inclusive em todos os comandos de saida. Os serializers de mensagens usam um `ObjectWriter` Jackson 2 com `WRITE_DATES_AS_TIMESTAMPS` desabilitado, sem modificar o mapper compartilhado ou a configuracao das APIs REST.

## Exchanges

- Comandos produzidos pelo OS Service: `oficina.commands`, topic duravel.
- Eventos recebidos pelo OS Service: `oficina.events`, topic duravel.
- DLQ do consumidor da saga: `os.saga.events.dlq`.

## Eventos Recebidos

| Evento | Payload minimo | Resultado |
| --- | --- | --- |
| `diagnostic.started.v1` | `{}` | OS `EM_DIAGNOSTICO` |
| `diagnostic.completed.v1` | `{"items":[{"itemId":"1","descricao":"...", "tipo":"SERVICO", "quantidade":"1", "unidadePreco":"UNIDADE", "precoPrevisto":"120.00"}]}` | cria `quote.create.v1` |
| `diagnostic.failed.v1` | `{"reason":"..."}` | compensacao antes de execucao |
| `quote.created.v1` | `{"quoteId":"Q-1"}` | OS `AGUARDANDO_APROVACAO` |
| `quote.creation-failed.v1` | `{"reason":"..."}` | compensacao antes de execucao |
| `quote.approved.v1` | `{}` | cria `repair.start.v1` |
| `quote.rejected.v1` | `{"reason":"..."}` | cancela OS antes de execucao |
| `repair.started.v1` | `{}` | OS `EM_EXECUCAO` |
| `repair.completed.v1` | `{"repairId":"R-1"}` | OS `AGUARDANDO_PAGAMENTO`, cria `payment.request.v1` |
| `repair.failed.v1` | `{"reason":"..."}` | cria intencao `repair.cancel.v1` e saga `COMPENSATING` |
| `repair.cancelled.v1` | `{}` | confirma compensacao e saga `COMPENSATED` |
| `repair.cancellation-failed.v1` | `{"reason":"..."}` | saga `FAILED` |
| `payment.confirmed.v1` | `{"paymentId":"P-1"}` | OS `FINALIZADA`, saga `COMPLETED` |
| `payment.failed.v1` | `{"reason":"..."}` | OS permanece `AGUARDANDO_PAGAMENTO`, saga permanece `WAITING_PAYMENT` |

## Comandos Produzidos

| Comando | Quando |
| --- | --- |
| `diagnostic.start.v1` | abertura da OS |
| `quote.create.v1` | diagnostico concluido |
| `repair.start.v1` | orcamento aprovado |
| `repair.cancel.v1` | falha de reparo com necessidade de intencao compensatoria |
| `payment.request.v1` | reparo concluido |

## Itens de Diagnostico

Cada item de `diagnostic.completed.v1` deve carregar dados suficientes para o Billing gerar o orcamento sem consultar bancos de outros servicos:

| Campo | Obrigatorio | Descricao |
| --- | --- | --- |
| `itemId` | Sim | Identificador do item no diagnostico |
| `descricao` | Sim | Descricao humana do servico, peca ou material |
| `tipo` | Sim | Categoria do item, por exemplo `SERVICO`, `PECA` ou `MATERIAL` |
| `quantidade` | Sim | Quantidade como string decimal para evitar perda de precisao entre linguagens |
| `unidadePreco` | Sim | Unidade da cobranca, por exemplo `UNIDADE`, `HORA` ou `KIT` |
| `precoPrevisto` | Sim | Valor unitario previsto como string decimal |

## Simulacao Manual

Publique no exchange `oficina.events` com routing key igual ao evento:

```bash
rabbitmqadmin publish exchange=oficina.events routing_key=diagnostic.started.v1 \
  payload='{"eventId":"00000000-0000-0000-0000-000000000001","eventType":"diagnostic.started.v1","occurredAt":"2026-10-12T12:00:00Z","correlationId":"00000000-0000-0000-0000-000000000002","sagaId":"<saga-id>","osId":"<os-id>","source":"diagnostic-service","payload":{}}'
```
