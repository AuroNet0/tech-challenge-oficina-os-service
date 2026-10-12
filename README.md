# Tech Challenge Oficina - OS Service

Microsservico da Fase 4 do Tech Challenge FIAP responsavel pelo contexto de ordens de servico da oficina mecanica.

## Responsabilidades

- Gerenciar ordens de servico, clientes e veiculos em etapas futuras.
- Orquestrar a Saga do fluxo de atendimento em etapas futuras.
- Manter persistencia propria em PostgreSQL.
- Expor health checks e documentacao OpenAPI.

Nesta etapa nao ha endpoints de negocio, entidades, RabbitMQ, Saga, Mercado Pago, CI/CD, Terraform, Kubernetes ou autenticacao de usuarios.

## Tecnologias

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- Spring Validation
- Spring Boot Actuator
- Spring Security
- PostgreSQL
- Flyway
- Springdoc OpenAPI
- JUnit 5
- Mockito
- Spring Boot Test
- Testcontainers
- JaCoCo
- Docker e Docker Compose

## Arquitetura de Pacotes

Pacote raiz: `com.oficina.os`.

```text
src/main/java/com/oficina/os/
├── OsApplication.java
└── infrastructure/
    └── config/
        ├── OpenApiConfig.java
        └── SecurityConfig.java
```

A estrutura hexagonal prevista para as proximas etapas e:

```text
domain/
├── model/
├── enums/
└── exception/
application/
├── usecase/
├── saga/
└── port/
    ├── in/
    └── out/
infrastructure/
├── persistence/
├── messaging/
├── web/
├── config/
└── observability/
```

Pacotes vazios nao foram criados porque esta etapa deve conter apenas classes necessarias.

## Variaveis de Ambiente

| Variavel | Padrao local | Descricao |
| --- | --- | --- |
| `DB_HOST` | `localhost` | Host do PostgreSQL |
| `DB_PORT` | `5432` | Porta do PostgreSQL |
| `DB_NAME` | `oficina_os` | Nome do banco |
| `DB_USERNAME` | `oficina_os` | Usuario do banco |
| `DB_PASSWORD` | `oficina_os` | Senha local de desenvolvimento |

Nao use credenciais reais nos arquivos versionados.

## Execucao Local

Suba um PostgreSQL local compativel com as variaveis acima e execute:

```bash
./mvnw spring-boot:run
```

No Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

A aplicacao fica disponivel em `http://localhost:8080`.

## Docker Compose

```bash
docker compose up --build
```

O Compose cria:

- `postgres`: PostgreSQL com volume persistente e healthcheck.
- `os-service`: aplicacao Spring Boot exposta em `http://localhost:8080`.

Para encerrar:

```bash
docker compose down
```

Para remover tambem o volume local:

```bash
docker compose down -v
```

## Banco e Migracoes

O Flyway executa as migracoes em `src/main/resources/db/migration`.

A migracao inicial `V1__bootstrap_schema.sql` cria apenas uma tabela tecnica (`os_service_bootstrap`) para validar o funcionamento do Flyway. O Hibernate esta configurado com `ddl-auto=validate`, sem criacao ou alteracao automatica de tabelas.

## Testes

Execute:

```bash
./mvnw test
```

No Windows PowerShell:

```powershell
.\mvnw.cmd test
```

Os testes de integracao usam Testcontainers com PostgreSQL. Docker precisa estar disponivel para esses testes.

## JaCoCo

Para rodar verificacao de cobertura e gerar relatorios HTML/XML:

```bash
./mvnw verify
```

No Windows PowerShell:

```powershell
.\mvnw.cmd verify
```

Relatorios:

- HTML: `target/site/jacoco/index.html`
- XML: `target/site/jacoco/jacoco.xml`

A regra minima configurada e 80% de classes cobertas, excluindo apenas a classe de bootstrap `OsApplication`.

## Health e Swagger

Endpoints publicos:

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /swagger-ui/index.html`
- `GET /v3/api-docs`

Demais endpoints exigem autenticacao pela configuracao inicial do Spring Security. A integracao JWT sera adicionada em etapa futura.
