# Tech Challenge Oficina - OS Service

Microsservico da Fase 4 do Tech Challenge FIAP responsavel pelo contexto de clientes, veiculos, filiais e ordens de servico.

## Tecnologias

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- Spring Validation
- Spring Security
- Spring AMQP / RabbitMQ
- PostgreSQL
- Flyway
- Springdoc OpenAPI
- JUnit 5, Mockito, Testcontainers e JaCoCo
- Docker e Docker Compose

## Arquitetura

Pacote raiz: `com.oficina.os`.

```text
domain/
  validation/
application/
  exception/
  usecase/
  port/
    in/
    out/
infrastructure/
  config/
  persistence/
  web/
    controller/
    request/
    response/
    exception/
```

As entidades de dominio nao dependem de Spring, JPA ou HTTP. Os controllers chamam casos de uso, que acessam persistencia por portas em `application.port.out`. Os adapters JPA ficam isolados em `infrastructure.persistence`.

## Banco e Migracoes

O Flyway executa migracoes em `src/main/resources/db/migration`.

- `V1__bootstrap_schema.sql`: tabela tecnica de bootstrap.
- `V2__create_os_domain.sql`: tabelas `clientes`, `veiculos`, `filiais`, `ordens_servico` e `historico_status`.
- `V3__create_outbox_events.sql`: tabela `outbox_events` para Transactional Outbox.
- `V4__create_saga_and_inbox.sql`: tabelas `saga_instances` e `inbox_messages`.
- `V5__operational_authorization.sql`: concessoes, atribuicoes e escopo cadastral cliente/veiculo por filial.
- `V6__branch_associations.sql`: capacidades de associacao e auditoria transacional. Nao reclassifica cadastros legados.

O Hibernate permanece com `ddl-auto=validate`.

## Endpoints

Todos os endpoints de negocio exigem autenticacao.

### Clientes

- `POST /api/v1/clientes?filialId={uuid}`
- `PUT /api/v1/clientes/{clienteId}/filiais/{filialDestinoId}`
- `GET /api/v1/clientes/{id}`
- `GET /api/v1/clientes`

Request:

```json
{
  "nome": "Maria Silva",
  "cpfCnpj": "529.982.247-25",
  "email": "maria@example.com",
  "telefone": "11999999999"
}
```

### Veiculos

- `POST /api/v1/veiculos?filialId={uuid}`
- `PUT /api/v1/veiculos/{veiculoId}/filiais/{filialDestinoId}`
- `GET /api/v1/veiculos/{id}`
- `GET /api/v1/veiculos?clienteId={uuid}`

Request:

```json
{
  "clienteId": "00000000-0000-0000-0000-000000000000",
  "placa": "ABC-1D23",
  "marca": "Fiat",
  "modelo": "Uno",
  "ano": 2020
}
```

### Filiais

- `POST /api/v1/filiais`
- `GET /api/v1/filiais/{id}`
- `GET /api/v1/filiais`

Request:

```json
{
  "nome": "Centro",
  "codigo": "SP-01",
  "cidade": "Sao Paulo",
  "uf": "SP"
}
```

### Ordens de Servico

- `POST /api/v1/ordens-servico`
- `GET /api/v1/ordens-servico/{id}`
- `GET /api/v1/ordens-servico`
- `GET /api/v1/ordens-servico/{id}/historico`
- `GET /api/v1/ordens-servico/{id}/saga`

Request de abertura:

```json
{
  "clienteId": "00000000-0000-0000-0000-000000000000",
  "veiculoId": "00000000-0000-0000-0000-000000000000",
  "filialId": "00000000-0000-0000-0000-000000000000",
  "observacoes": "Barulho no motor"
}
```

Consulta paginada:

```text
GET /api/v1/ordens-servico?clienteId={uuid}&veiculoId={uuid}&filialId={uuid}&status=RECEBIDA&page=0&size=20
```

Os filtros sao opcionais e aplicados em conjunto no banco. A ordenacao padrao e por `createdAt` decrescente.

Resposta de OS:

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "clienteId": "00000000-0000-0000-0000-000000000000",
  "veiculoId": "00000000-0000-0000-0000-000000000000",
  "filialId": "00000000-0000-0000-0000-000000000000",
  "status": "RECEBIDA",
  "observacoes": "Barulho no motor",
  "createdAt": "2026-10-12T12:00:00Z",
  "updatedAt": "2026-10-12T12:00:00Z",
  "startedAt": null,
  "finishedAt": null,
  "version": 0
}
```

## Regras de Validacao

- CPF/CNPJ e normalizado para digitos e validado por digitos verificadores.
- CPF/CNPJ duplicado retorna `409`.
- Placa e normalizada para maiusculas sem separadores e aceita formatos antigo e Mercosul.
- Placa duplicada retorna `409`.
- Veiculo exige cliente existente e ativo.
- Filial nasce ativa e codigo e normalizado.
- OS nasce `RECEBIDA`.
- Abertura de OS exige cliente ativo, filial ativa e veiculo pertencente ao cliente.
- A OS criada registra historico inicial `RECEBIDA` na mesma transacao local.
- A abertura de OS tambem cria uma `SagaInstance` e um `OutboxEvent` `PENDING` com o comando `diagnostic.start.v1` na mesma transacao local.
- Nao ha endpoint HTTP para avancar status manualmente.

## Transactional Outbox e RabbitMQ

A abertura da OS nao publica diretamente no RabbitMQ. O caso de uso persiste, na mesma transacao PostgreSQL:

1. `ordens_servico`
2. `historico_status`
3. `saga_instances`
4. `outbox_events`

Depois do commit, um publisher independente consulta a outbox e envia o comando para o broker.

```text
POST /ordens-servico
  -> OrdemServicoUseCase.abrir()
  -> PostgreSQL transaction
       ordens_servico
       historico_status
       saga_instances(WAITING_DIAGNOSTIC)
       outbox_events(PENDING)
  -> HTTP 201

OutboxPublisher
  -> acquire batch with lease
  -> RabbitMQ publish with mandatory + publisher confirm
  -> PUBLISHED or retry/FAILED
```

### Topologia RabbitMQ

| Item | Valor |
| --- | --- |
| Exchange | `oficina.commands` |
| Tipo | `topic`, duravel |
| Routing key | `diagnostic.start.v1` |
| Fila temporaria do OS Service | `execution.diagnostic.start` |

O OS Service tambem declara a exchange duravel topic `oficina.events`, a fila `os.saga.events` e a DLQ `os.saga.events.dlq` para respostas dos servicos externos. Eventos versionados `*.v1` sao consumidos pelo Saga Orchestrator.

A fila e o binding sao declarados pelo OS Service apenas para desenvolvimento local e testes. Quando o Execution Service existir, essa declaracao deve ir para o consumidor ou para infraestrutura compartilhada.

### Contrato JSON

```json
{
  "eventId": "uuid",
  "eventType": "diagnostic.start.v1",
  "occurredAt": "2026-10-12T12:00:00Z",
  "correlationId": "uuid",
  "sagaId": "uuid",
  "osId": "uuid",
  "source": "os-service",
  "payload": {
    "clienteId": "uuid",
    "veiculoId": "uuid",
    "filialId": "uuid",
    "observacoes": "Barulho no motor"
  }
}
```

`diagnostic.start.v1` e um comando, mesmo usando o campo padronizado `eventType`. O `eventId` e gerado uma unica vez durante a abertura da OS e preservado nos retries. `sagaId` identifica a instancia persistente criada na abertura.

### Publicacao, Retry e Concorrencia

O publisher processa lotes periodicos, usa mensagens persistentes, `publisher-confirm-type=correlated`, `mandatory=true` e valida mensagens retornadas como nao roteaveis. Eventos so viram `PUBLISHED` apos confirmacao positiva do broker.

A aquisicao usa lease persistido em `locked_until` com `SELECT FOR UPDATE SKIP LOCKED`. Isso evita que duas instancias processem o mesmo registro ao mesmo tempo. Se a aplicacao cair com evento em `PROCESSING`, ele volta a ficar elegivel quando `locked_until` expirar.

Falhas incrementam `attempts` e reagendam `next_attempt_at` com backoff exponencial limitado. Apos `OUTBOX_PUBLISHER_MAX_ATTEMPTS`, o evento passa para `FAILED` para investigacao e reprocessamento controlado. A confirmacao de publicacao usa atualizacao condicional por lease para evitar que um worker com lease expirado sobrescreva o estado assumido por outro.

Limite conhecido: se o RabbitMQ confirmar a publicacao e a atualizacao do PostgreSQL falhar logo depois, a mensagem pode ser publicada novamente. O futuro consumidor deve deduplicar por `eventId`.

### Configuracoes

| Variavel | Padrao local |
| --- | --- |
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USERNAME` | `guest` |
| `RABBITMQ_PASSWORD` | `guest` |
| `RABBITMQ_MANAGEMENT_PORT` | `15672` |
| `RABBITMQ_COMMANDS_EXCHANGE` | `oficina.commands` |
| `RABBITMQ_EVENTS_EXCHANGE` | `oficina.events` |
| `RABBITMQ_DIAGNOSTIC_START_ROUTING_KEY` | `diagnostic.start.v1` |
| `RABBITMQ_DIAGNOSTIC_START_QUEUE` | `execution.diagnostic.start` |
| `RABBITMQ_SAGA_EVENTS_QUEUE` | `os.saga.events` |
| `RABBITMQ_SAGA_EVENTS_DLQ` | `os.saga.events.dlq` |
| `OUTBOX_PUBLISHER_ENABLED` | `true` |
| `OUTBOX_PUBLISHER_BATCH_SIZE` | `10` |
| `OUTBOX_PUBLISHER_FIXED_DELAY` | `5s` |
| `OUTBOX_PUBLISHER_LEASE_DURATION` | `1m` |
| `OUTBOX_PUBLISHER_MAX_ATTEMPTS` | `5` |
| `OUTBOX_PUBLISHER_INITIAL_BACKOFF` | `10s` |
| `OUTBOX_PUBLISHER_MAX_BACKOFF` | `5m` |

Consultar mensagens pendentes:

```sql
SELECT id, aggregate_id, event_type, status, attempts, next_attempt_at, locked_until, last_error
FROM outbox_events
WHERE status IN ('PENDING', 'PROCESSING', 'FAILED')
ORDER BY created_at;
```

Testar indisponibilidade do broker:

```bash
docker compose stop rabbitmq
curl -H "Authorization: Bearer ${TOKEN_ATENDENTE}" -H "Content-Type: application/json" -d @request.json http://localhost:8080/api/v1/ordens-servico
docker compose start rabbitmq
```

A resposta HTTP de abertura pode ser `201` com RabbitMQ indisponivel, porque o commit local ja gravou o `OutboxEvent`. O publisher fara retry quando o broker voltar.

## Erros

Os erros seguem `ProblemDetail` (`application/problem+json`).

- `400`: entrada invalida, UUID invalido, validacao de request.
- `401`: JWT ausente ou invalido.
- `403`: papel, capacidade, escopo ou legitimidade da associacao insuficiente.
- `404`: recurso inexistente.
- `409`: duplicidade, conflito de negocio ou optimistic locking.
- `500`: erro inesperado sem stack trace no payload.
- `503`: dependencia indispensavel de autorizacao indisponivel; vinculo de cliente ainda bloqueado.

## Seguranca JWT e autorizacao por recurso

Somente os healthchecks abaixo sao publicos:

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
Negocio exige JWT RS256 do Auth, issuer/audience/JWKS configurados e capacidades operacionais no banco OS. HTTP Basic nao e aceito. GERENTE nao e superusuario; MECANICO exige atribuicao individual a OS e usa as projecoes `/ordens-servico/{id}/operacional` e `/ordens-servico/{id}/veiculo-operacional`. Swagger/OpenAPI ficam desabilitados e negados pela politica HTTP.

Exemplo local:

```bash
curl -H "Authorization: Bearer ${TOKEN_ATENDENTE}" http://localhost:8080/api/v1/clientes
```

O canal confiavel de vinculos Auth ainda nao esta ativo: consultas de clientes finais que dependem dele permanecem fechadas. Nao existem endpoints de autoconcessao, seeds privilegiados ou provisionamento administrativo publico. Consulte [a politica completa](docs/security/os-authorization-8.3.2-b.md).

### Atendimento em multiplas filiais

Politica implementada: o ATENDENTE identifica o cadastro pelos GETs ja autorizados na origem. Para associa-lo, precisa de CLIENT_READ na origem e CLIENT_ASSOCIATE no destino. Para veiculo, exige tambem VEHICLE_READ na origem e VEHICLE_ASSOCIATE no destino, pertencimento ao cliente informado e cliente ja associado ao destino. As capacidades CREATE nao substituem ASSOCIATE. Esta delegacao explicita permite compartilhar apenas cadastros ja conhecidos pelo funcionario; nao existe busca global por CPF/placa ou prova baseada apenas em UUID.

PUT de cliente (o destino esta no caminho):

```http
PUT /api/v1/clientes/{clienteId}/filiais/{filialDestinoId}
Authorization: Bearer <JWT ATENDENTE>
Content-Type: application/json

{"filialOrigemId":"<UUID origem>","motivo":"Atendimento na filial de destino"}
```

PUT de veiculo, depois da associacao do cliente:

```http
PUT /api/v1/veiculos/{veiculoId}/filiais/{filialDestinoId}
Authorization: Bearer <JWT ATENDENTE>
Content-Type: application/json

{"clienteId":"<UUID cliente>","filialOrigemId":"<UUID origem>","motivo":"Atendimento na filial de destino"}
```

Ambos retornam **204 sem corpo**, inclusive em repeticoes. Motivo e obrigatorio, ate 200 caracteres, e nao deve conter dados pessoais. Origem/destino/cliente devem estar ativos; veiculo nao possui estado ativo proprio. Identificador inexistente, cadastro fora da origem autorizada ou legado sem classificacao retornam 403 generico, sem revelar cadastro. Estado inativo de recurso ja autorizado retorna 409. A origem tambem pode coincidir com o destino: as mesmas permissoes sao exigidas e a operacao permanece idempotente.

As chaves compostas impedem duplicacao; cada PUT aceito audita ator autenticado, cliente/veiculo, origem, destino, motivo, instante e se uma nova associacao foi criada. Falha da auditoria reverte a associacao. Repeticoes concorrentes mantem um unico vinculo e auditam cada operacao aceita. Nao ha transferencia de proprietario ou de OS historica, nem concessao de permissao ao atendente. Os GETs existentes continuam sujeitos a suas capacidades de leitura; associar nao concede OS_READ nem acesso a historico de outra filial.

Cadastros apenas conhecidos na filial de destino, sem leitura autorizada de uma origem, ficam **bloqueados**. Um funcionario da origem com delegacao especifica no destino pode executar a associacao. Identificacao global, consentimento remoto, handoff entre funcionarios e classificacao inicial de legados precisam de fluxo confiavel futuro; nao ha liberacao por CPF, placa ou conhecimento do UUID.

### Preparacao dos scripts E2E

1. Em ambiente isolado, obter usuarios Auth e JWTs validos por processo administrativo confiavel. Nao criar usuarios privilegiados automaticamente nem usar Basic.
2. Preparar filiais A/B e concessoes explicitas por operador autorizado ou fixtures exclusivamente de teste. Nao ha endpoint publico de provisionamento. Para criar filiais via HTTP, GERENTE precisa de BRANCH_CREATE especifico.
3. Conceder ATENDENTE + CLIENT_CREATE/VEHICLE_CREATE na A para o cadastro inicial; CLIENT_READ/VEHICLE_READ na A e CLIENT_ASSOCIATE/VEHICLE_ASSOCIATE na B para a associacao. Preparar tambem OS_OPEN/OS_READ em cada filial conforme o cenario, sem concessao universal.
4. Criar cliente e veiculo com `?filialId=A`; abrir uma OS em A usando o JSON anterior. Identificar os IDs apenas pelos cadastros/GETs autorizados.
5. Executar os dois PUTs acima para B, primeiro cliente e depois veiculo. Repetir para comprovar 204/idempotencia; nao recriar CPF/placa.
6. Abrir nova OS com o mesmo clienteId/veiculoId e filialId=B. A OS antiga e seu historico continuam em A. Conferir paginacao com um segundo usuario restrito a B: somente OS de B, inclusive no total.
7. Verificar ausencia/revogacao de capacidade, papel incorreto, origem nao autorizada e UUID inexistente: nenhuma associacao ou auditoria de sucesso deve ser criada. Recursos de cliente final continuam bloqueados ate o canal Auth confiavel.

## Execucao Local

Com PostgreSQL local compativel com as variaveis abaixo:

```bash
./mvnw spring-boot:run
```

Variaveis:

| Variavel | Padrao local |
| --- | --- |
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `oficina_os` |
| `DB_USERNAME` | `oficina_os` |
| `DB_PASSWORD` | `oficina_os` |
| `AUTH_JWT_ISSUER` | obrigatorio, sem padrao |
| `AUTH_JWT_AUDIENCE` | obrigatorio, sem padrao |
| `AUTH_JWKS_URI` | obrigatorio, sem padrao; URL confiavel do JWKS publico |
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USERNAME` | `guest` |
| `RABBITMQ_PASSWORD` | `guest` |

## Docker Compose

```bash
docker compose up --build
```

Servicos locais:

- API: `http://localhost:8080`
- RabbitMQ Management UI: `http://localhost:15672`
- PostgreSQL: `localhost:5432`

Para encerrar:

```bash
docker compose down
```

Para remover tambem o volume:

```bash
docker compose down -v
```

## Swagger

Desabilitado por padrao e negado pela politica HTTP. A flag OPENAPI_ENABLED isolada nao autoriza sua exposicao.

## Testes e Cobertura

```bash
./mvnw clean verify
```

Os testes de integracao usam Testcontainers com PostgreSQL e RabbitMQ, alem de JWKS local de teste. Docker e Java 21 precisam estar disponiveis; nao ha dependencia AWS real. No Windows, usar `mvnw.cmd clean verify` ou a distribuicao Maven local equivalente.

Relatorios JaCoCo:

- HTML: `target/site/jacoco/index.html`
- XML: `target/site/jacoco/jacoco.xml`

A regra minima configurada e 80% de cobertura por linhas, excluindo apenas a classe de bootstrap `OsApplication`.

Validacao complementar em 26/10/2026: `clean verify` aprovado, **121 testes**, zero falhas/erros/ignorados, **JaCoCo 93,15%** (1.563/1.678 linhas). Inclui upgrade V4 populada → V5 → V6, associacoes concorrentes, rollback de auditoria, escopo paginado e suites anteriores de Saga/RabbitMQ. Evidencias em `target/os-branch-association-verify.log` e na documentacao de seguranca.

## Saga Orchestrator

A primeira versao persistente da saga coordena diagnostico, orcamento, reparo e pagamento com RabbitMQ, Transactional Outbox e Transactional Inbox. A abertura inicia a saga em `WAITING_DIAGNOSTIC` e produz `diagnostic.start.v1`. Consulte [docs/saga.md](docs/saga.md) e [docs/events.md](docs/events.md).

## Fora do Escopo Atual

- Mercado Pago
- Billing Service
- Execution Service
- Atualizacao manual de status por HTTP
- CRUD completo com edicao e exclusao
- Terraform, Kubernetes ou CI/CD
