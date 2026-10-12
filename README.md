# Tech Challenge Oficina - OS Service

Microsservico da Fase 4 do Tech Challenge FIAP responsavel pelo contexto de clientes, veiculos, filiais e ordens de servico.

## Tecnologias

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- Spring Validation
- Spring Security
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

O Hibernate permanece com `ddl-auto=validate`.

## Endpoints

Todos os endpoints de negocio exigem autenticacao.

### Clientes

- `POST /api/v1/clientes`
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

- `POST /api/v1/veiculos`
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
- Nao ha endpoint HTTP para avancar status manualmente.

## Erros

Os erros seguem `ProblemDetail` (`application/problem+json`).

- `400`: entrada invalida, UUID invalido, validacao de request.
- `404`: recurso inexistente.
- `409`: duplicidade, conflito de negocio ou optimistic locking.
- `500`: erro inesperado sem stack trace no payload.

## Seguranca Temporaria

Health e Swagger sao publicos:

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /swagger-ui/index.html`
- `GET /v3/api-docs`

Endpoints de negocio exigem autenticacao HTTP Basic temporaria do Spring Security. Ao iniciar a aplicacao, o log exibe a senha gerada para o usuario padrao `user`.

Exemplo local:

```bash
curl -u user:<senha-gerada> http://localhost:8080/api/v1/clientes
```

JWT definitivo e roles de `ATENDENTE`, `MECANICO` e `GERENTE` serao tratados em etapa futura.

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

## Docker Compose

```bash
docker compose up --build
```

Para encerrar:

```bash
docker compose down
```

Para remover tambem o volume:

```bash
docker compose down -v
```

## Swagger

Com a aplicacao no ar:

```text
http://localhost:8080/swagger-ui/index.html
```

## Testes e Cobertura

```bash
./mvnw clean verify
```

Os testes de integracao usam Testcontainers com PostgreSQL. Docker precisa estar disponivel.

Relatorios JaCoCo:

- HTML: `target/site/jacoco/index.html`
- XML: `target/site/jacoco/jacoco.xml`

A regra minima configurada e 80% de cobertura por linhas, excluindo apenas a classe de bootstrap `OsApplication`.

## Fora do Escopo Atual

- RabbitMQ
- Saga
- Outbox/Inbox
- JWT definitivo
- Mercado Pago
- Billing Service
- Execution Service
- Atualizacao manual de status por HTTP
- CRUD completo com edicao e exclusao
- Terraform, Kubernetes ou CI/CD
