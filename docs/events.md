# Eventos e comandos RabbitMQ

Fontes: `SagaEventType`, `SagaMessageSerializer`, `SagaEventHandler`, `RabbitMessagingConfig`, `RabbitMessageBrokerAdapter`, `OutboxPublisher` e [application.yml](../src/main/resources/application.yml). Consulte [Saga](saga.md) para as condições de estado e [README](../README.md#5-mensageria) para a visão do serviço.

## Envelope

| Campo | Uso confirmado |
| --- | --- |
| `eventId` | UUID de mensagem; deduplicação da Inbox e identificação da Outbox |
| `eventType` | Tipo versionado; routing key usada na publicação pelo OS |
| `occurredAt` | Instante do envelope; os comandos de saída usam string ISO-8601 |
| `correlationId` | Correlação propagada nos comandos derivados e histórico |
| `sagaId`, `osId` | Identificam Saga e OS; o handler verifica correspondência |
| `source` | Comandos do OS usam `os-service` |
| `payload` | Dados específicos do tipo |

Os serializers usam `ObjectWriter` Jackson com `WRITE_DATES_AS_TIMESTAMPS` desabilitado para os comandos. Isso não significa que o handler valide todos os campos do envelope recebido: a validação explícita inicial exige `eventId`, `eventType`, `correlationId`, `sagaId`, `osId` e `payload`; não valida explicitamente `source` ou presença de `occurredAt`.

## Exchanges, filas e bindings

Nomes padrão, substituíveis pelas propriedades de mensageria:

| Tipo | Nome | Binding / comportamento |
| --- | --- | --- |
| Exchange topic durável | `oficina.commands` | Recebe comandos publicados pelo OS |
| Fila durável | `execution.diagnostic.start` | `diagnostic.start.v1` em `oficina.commands` |
| Exchange topic durável | `oficina.events` | Entrada de eventos |
| Fila durável | `os.saga.events` | `#.v1` em `oficina.events`; consumida por `SagaEventConsumer` |
| Exchange topic durável | `oficina.events.dlx` | Dead-letter exchange da fila da Saga |
| Fila durável | `os.saga.events.dlq` | `saga.events.dead` em `oficina.events.dlx` |

A exchange DLX deriva do nome configurado da exchange de eventos acrescido de `.dlx`. A fila da Saga define essa DLX e routing key de dead-letter `saga.events.dead`.

A declaração de `execution.diagnostic.start` não possui restrição de perfil no código. Outras filas de consumidores de comandos não são declaradas neste serviço. O binding `#.v1` é mais amplo que a lista de tipos tratados: tipo não suportado, com envelope válido e Saga/OS correspondentes, é rejeitado pelo handler na Inbox.

## Comandos publicados

Todos são gravados transacionalmente na Outbox e publicados em `oficina.commands`, usando `eventType` como routing key. Não foi identificada publicação de eventos de negócio pelo OS.

| Comando | Gatilho | Payload |
| --- | --- | --- |
| `diagnostic.start.v1` | Abertura da OS | `clienteId`, `veiculoId`, `filialId`, `observacoes` |
| `quote.create.v1` | `diagnostic.completed.v1` | `items` recebidos e validados |
| `repair.start.v1` | `quote.approved.v1` | Objeto vazio |
| `repair.cancel.v1` | `repair.failed.v1` | `reason` |
| `payment.request.v1` | `repair.completed.v1` | `repairId` |

O identificador do comando é gerado uma vez ao persistir a Outbox e preservado nas tentativas de publicação.

## Eventos consumidos

As condições de etapa/status são obrigatórias; a tabela resume o resultado quando o evento é compatível.

| Evento | Payload utilizado | Resultado |
| --- | --- | --- |
| `diagnostic.started.v1` | Objeto não nulo | OS `EM_DIAGNOSTICO` |
| `diagnostic.completed.v1` | `items` não vazio | `quote.create.v1`; Saga `WAITING_QUOTE` |
| `diagnostic.failed.v1` | `reason`, opcional | Cancela OS; Saga `COMPENSATED` |
| `quote.created.v1` | `quoteId` não vazio | OS `AGUARDANDO_APROVACAO`; Saga `WAITING_APPROVAL` |
| `quote.creation-failed.v1` | `reason`, opcional | Cancela OS; Saga `COMPENSATED` |
| `quote.approved.v1` | Objeto não nulo | `repair.start.v1`; Saga `WAITING_REPAIR` |
| `quote.rejected.v1` | Objeto não nulo; motivo do payload não é utilizado | Cancela OS; Saga `COMPENSATED` |
| `repair.started.v1` | Objeto não nulo | OS `EM_EXECUCAO` |
| `repair.completed.v1` | `repairId` não vazio | OS `AGUARDANDO_PAGAMENTO`; `payment.request.v1`; Saga `WAITING_PAYMENT` |
| `repair.failed.v1` | `reason`, opcional | `repair.cancel.v1`; Saga `COMPENSATING` |
| `repair.cancelled.v1` | Objeto não nulo | Saga `COMPENSATED`; OS cancelada apenas nos estados permitidos |
| `repair.cancellation-failed.v1` | `reason`, opcional | Saga `FAILED`; OS preservada |
| `payment.confirmed.v1` | `paymentId` não vazio | OS `FINALIZADA`; Saga `COMPLETED` |
| `payment.failed.v1` | `reason`, opcional | Mantém OS `AGUARDANDO_PAGAMENTO` e Saga `WAITING_PAYMENT`; registra erro |

Quando `failureReason` é utilizado, `reason` ausente ou em branco resulta no próprio `eventType` como motivo. Falha de pagamento não gera novo comando de cobrança.

## Itens de diagnóstico

Cada item deve conter os campos textuais abaixo, todos não vazios. O OS os encaminha ao comando de orçamento.

| Campo | Validação no handler |
| --- | --- |
| `itemId` | Texto não vazio |
| `descricao` | Texto não vazio |
| `tipo` | Texto não vazio |
| `quantidade` | Texto não vazio |
| `unidadePreco` | Texto não vazio |
| `precoPrevisto` | Texto não vazio |

O OS não valida enumerações de tipo/unidade nem interpreta numericamente quantidade/preço nesse handler. A documentação anterior apresentava exemplos de categorias e formatos; esses exemplos não constituem regras implementadas de validação.

## Entrega, retry e DLQ

A publicação usa mensagem persistente, `mandatory=true`, confirms correlacionados e conferência de retorno não roteável. Só após confirmação positiva a Outbox passa a `PUBLISHED`.

A aquisição usa `FOR UPDATE SKIP LOCKED` e lease em `locked_until`; itens `PROCESSING` com lease expirado podem ser retomados. A atualização do resultado pelo publisher verifica o lease. Há possibilidade de republicação se o broker confirmar e a atualização no banco falhar: a entrega admite duplicatas (at-least-once).

Padrões configurados:

| Propriedade do publisher | Padrão |
| --- | --- |
| `batch-size` | 10 |
| `fixed-delay` | 5 s |
| `lease-duration` | 1 min |
| `max-attempts` | 5 |
| `initial-backoff` | 10 s |
| `max-backoff` | 5 min |
| `oficina.messaging.confirm-timeout` | 5 s |

Falhas de publicação incrementam tentativas e aplicam backoff exponencial limitado. Ao esgotar tentativas, o registro fica `FAILED` na Outbox. Não é movido à DLQ e não há endpoint de reprocessamento identificado.

A Inbox deduplica `eventId` já `PROCESSED` ou `REJECTED`. Rejeições de negócio capturadas pelo handler são persistidas e retornam normalmente ao listener, sem encaminhamento automático à DLQ. Falhas anteriores ao bloco de tratamento e falhas técnicas não capturadas propagam ao listener.

A DLQ está configurada para dead-lettering da fila de entrada. Não há política explícita de retry limitado do listener, fila de retry, TTL de retry ou redrive automático neste repositório; portanto, não se afirma que qualquer exceção terminará na DLQ.

## Verificação dos contratos

`SagaMessageSerializerTest` verifica serialização; `SagaEventHandlerTest` verifica transições; `SagaFailureIntegrationTest` verifica rejeição por etapa; `OutboxPublisherTest`, testes de persistência/rollback e `RabbitMessageBrokerAdapterIntegrationTest` verificam publicação e persistência. Execute `./mvnw clean verify` com Java 21 e Docker disponível para Testcontainers.

As implementações dos produtores e consumidores externos não são verificadas por estes contratos locais.
