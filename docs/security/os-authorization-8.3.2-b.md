# OS Service — autorização 8.3.2-B

Data: 25/10/2026; complemento em 26/10/2026 na seção 12. Branch inicial `homolog`, HEAD `6b3e11f77f873068d1e6b1e68f71586bd67adea9`, árvore inicialmente limpa na entrega original. O complemento preserva todas as alterações locais dessa entrega. Nenhum commit/push. Escopo exclusivo do OS; a etapa 8.3.3 não foi iniciada.

## 1. Referências e versões

Foram consultados os quatro contratos do repositório Auth adjacente: `docs/authentication.md`, `docs/security/authorization-8.3.1.md`, `docs/security/internal-identity-8.3.2-a.md` e `docs/security/internal-service-auth-8.3.2-a1.md`. Nenhum arquivo Auth foi alterado.

OS utiliza Java 21, Spring Boot **4.1.1**, Spring Security **7.1.1** gerenciado pelo BOM. Foi adicionado `spring-boot-starter-security-oauth2-resource-server`, sem fixar versões paralelas de Security/Nimbus. Compatibilidade comprovada pela compilação e testes desta versão. Referência primária: [Spring Security Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

Antes das alterações foram revistos SecurityConfig, os quatro controllers, use cases, Cliente/Veiculo/Filial/OrdemServico, repositories/mapeadores JPA, migrações V1–V4, Orquestrador/handler/consumer Saga, Inbox/Outbox/publisher, RabbitMessagingConfig, ClientAccessPort, ClientAccessHttpAdapter, SigV4ClientAccessSigner e testes existentes.

## 2. Autenticação JWT e configuração

`SecurityConfig` configura Resource Server Bearer, sessão STATELESS, sem Basic/form login e sem hierarquia. CSRF desabilitado para esta API sem autenticação por cookie. Somente `roles` origina authorities; claims `scope` e headers declarativos não concedem acesso.

Configuração obrigatória, sem URLs produtivas inventadas ou valores padrão:

| Variável | Propriedade | Conteúdo |
| --- | --- | --- |
| `AUTH_JWT_ISSUER` | `oficina.security.jwt.issuer` | Emissor exato do Auth |
| `AUTH_JWT_AUDIENCE` | `oficina.security.jwt.audience` | Audiência aprovada no Auth |
| `AUTH_JWKS_URI` | `oficina.security.jwt.jwks-uri` | URL confiável do JWKS público |

Compose repassa essas variáveis e exige seu preenchimento. Em ambiente implantado, usar HTTPS com CA confiável para issuer/JWKS e restringir quem pode alterar essas configurações. O HTTP em loopback pertence exclusivamente ao servidor JWKS de testes. Não há discovery obrigatório nem consulta ao banco Auth por token. Chaves públicas são obtidas pelo Nimbus com cache e renovação de JWKS; conexão/leitura HTTP têm limite de 2 segundos. Nenhuma chave privada Auth foi copiada.

`AuthJwtDecoder` aplica allowlist RS256 e assinatura real pelo Nimbus. Antes da conversão de claims, examina o payload para preservar tipos (por exemplo, impedir `aud` string no contrato que exige array).

| Elemento | Validação |
| --- | --- |
| Header | `alg=RS256`, `typ=JWT`, `kid` textual não vazio; somente esses campos |
| Assinatura | RSA/JWKS configurado; rejeita none, HS256, RS384 e chave desconhecida/incorreta |
| `iss` | Correspondência exata ao configurado |
| `aud` | Array textual não vazio contendo audiência configurada |
| `sub`, `jti` | UUID canônico minúsculo, sem forma abreviada |
| `iat`, `nbf`, `exp` | NumericDate inteiro obrigatório; iat não negativo/não futuro; nbf=iat; exp posterior a agora/iat; TTL máximo 900 segundos |
| Relógio | Skew zero, compatível com verificador interno atual do Auth; Clock injetável |
| `roles` | Array de nomes conhecidos, sem duplicatas/tipos mistos; vazio é válido sem permissões |
| Limites | Token até 8 KiB; erros criptográficos/HTTP de JWKS sanitizados |

Mapeamento direto: CLIENTE → ROLE_CLIENTE, ATENDENTE → ROLE_ATENDENTE, MECANICO → ROLE_MECANICO, GERENTE → ROLE_GERENTE. GERENTE não herda nenhum outro papel.

## 3. Fronteira de aplicação e autorização de recurso

Controllers chamam exclusivamente `AuthorizedBusinessUseCase`. Esse caso de uso HTTP depende da porta `CurrentActor`, que fornece somente UUID do usuário Auth e papéis autenticados. `HttpCurrentActor` exige JwtAuthenticationToken autenticado da invocação atual; não aceita user/password Authentication, X-User-Id, X-Roles ou token recebido como argumento de negócio. Também implementa `AuthenticatedUserToken` devolvendo o JWT original validado. Nenhum estado de identidade é guardado em singleton ou entre requests.

`ResourceAuthorization` concentra a política e depende de `OperationalAccessPort` e `ClientAccessPort`. Entidades de domínio e processamento interno não dependem de SecurityContextHolder. Os use cases preexistentes preservam validação de domínio e são internos; não constituem entradas HTTP sem autorização.

Funcionários: papel compatível → resolução do alvo → filial/atribuição e capacidade ativa → execução. Escritas autorizadas rodam em transação envolvendo cadastro/escopo ou OS/histórico/Saga/Outbox. A abertura exige OS_OPEN e cadastros cliente/veículo na filial solicitada, antes de qualquer save. O domínio continua verificando pertencimento do veículo ao cliente e estados ativos. JDBC faz flush da persistência JPA antes de registrar FKs de cadastro, mantendo rollback conjunto.

Clientes: papel CLIENTE → resolução do cliente contratante do recurso → decisão atual pela porta Auth → acesso somente se true. Não existe comparação de `sub` com `clienteId`. Papéis mistos seguem o caminho operacional quando ATENDENTE/GERENTE está presente; uma concessão ausente não vira acesso global nem fallback implícito.

## 4. Matriz HTTP efetivamente implementada

Todos os caminhos de negócio têm prefixo `/api/v1`. Somente os métodos listados são reconhecidos pela política; restante deny-all.

| Método/caminho | Papel | Condição adicional |
| --- | --- | --- |
| POST `/clientes?filialId=...` | ATENDENTE | CLIENT_CREATE nessa filial |
| PUT `/clientes/{clienteId}/filiais/{filialId}` | ATENDENTE | CLIENT_ASSOCIATE no destino; CLIENT_READ e cadastro classificado na origem |
| GET `/clientes` | ATENDENTE | Apenas cadastros associados a filiais com CLIENT_READ |
| GET `/clientes/{id}` | ATENDENTE | Cadastro associado a filial com CLIENT_READ |
| POST `/veiculos?filialId=...` | ATENDENTE | VEHICLE_CREATE; cliente cadastrado na mesma filial |
| PUT `/veiculos/{veiculoId}/filiais/{filialId}` | ATENDENTE | VEHICLE_ASSOCIATE no destino; CLIENT_READ/VEHICLE_READ e ambos cadastros na origem; cliente já classificado no destino |
| GET `/veiculos?clienteId=...` | ATENDENTE ou CLIENTE | VEHICLE_READ/escopo de cadastro; cliente depende do Auth bloqueado |
| GET `/veiculos/{id}` | ATENDENTE ou CLIENTE | Idem, resolvendo clienteId real do veículo |
| POST `/filiais` | GERENTE | Concessão administrativa BRANCH_CREATE |
| GET `/filiais`, GET `/filiais/{id}` | ATENDENTE ou GERENTE | BRANCH_READ explícito |
| POST `/ordens-servico` | ATENDENTE | OS_OPEN na filial; cliente/veículo cadastrados nela |
| GET `/ordens-servico` | ATENDENTE ou GERENTE ou CLIENTE | OS_READ na consulta SQL; cliente exige clienteId e Auth positivo, hoje bloqueado |
| GET `/ordens-servico/{id}` | ATENDENTE ou GERENTE ou CLIENTE | OS_READ da filial ou Auth positivo, hoje bloqueado |
| GET `/ordens-servico/{id}/historico` | ATENDENTE ou GERENTE ou CLIENTE | Mesma autorização da OS |
| GET `/ordens-servico/{id}/saga` | GERENTE | SAGA_INSPECT explícito na filial do recurso |
| GET `/ordens-servico/{id}/operacional` | MECANICO | Atribuição OPERATIONAL_READ nessa OS |
| GET `/ordens-servico/{id}/veiculo-operacional` | MECANICO | Atribuição VEHICLE_OPERATIONAL_READ nessa OS |

CLIENTE não abre OS. MECANICO não recebe cadastro integral de cliente, listagem global de veículos ou DTO integral da OS. Nenhum endpoint conclui diagnóstico/reparo, altera status, aprova orçamento ou confirma pagamentos. GERENTE não possui escrita técnica nem acesso universal a PII. A capacidade SAGA_INSPECT não é concedida pelo papel ou por OS_READ.

Respostas: 401 para Bearer ausente/inválido, 403 para papel/capacidade/escopo insuficiente. Recurso inexistente resolvido antes de checar escopo pode produzir 404; recurso existente de terceiros produz 403 genérico. Não há promessa de ocultação temporal de existência. Falhas indispensáveis de conexão/transação/timeout de autorização e ClientAccessException produzem 503 sanitizado; nunca decisão positiva. Erros de segurança não incluem stack trace, URL JWKS ou descrição remota; 401 informa somente `WWW-Authenticate: Bearer`.

GET `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` permanecem públicos e sem detalhes. Nenhuma probe exige JWT de usuário. Actuator expõe somente health. OpenAPI/Swagger ficam desabilitados por padrão e negados pela política HTTP mesmo com OPENAPI_ENABLED=true: a flag isolada não publica documentação. Eventual exposição autenticada exige política futura deliberada. Saga nunca é endpoint técnico público.

## 5. Modelo persistente, capacidades e provisionamento

Migração original **V5__operational_authorization.sql** preservada. O complemento acrescenta **V6__branch_associations.sql**, com capacidades de associação e auditoria; V1–V5 não foram alteradas.

| Tabela | Alvo/capacidades |
| --- | --- |
| `operational_grants` | filial; OS_READ, OS_OPEN, CLIENT_READ, CLIENT_CREATE, VEHICLE_READ, VEHICLE_CREATE, BRANCH_READ |
| Capacidades acrescentadas pela V6 | CLIENT_ASSOCIATE e VEHICLE_ASSOCIATE, independentes de CREATE/READ |
| `mechanic_assignments` | OS; OPERATIONAL_READ ou VEHICLE_OPERATIONAL_READ |
| `administrative_grants` | BRANCH_CREATE sem alvo preexistente; SAGA_INSPECT com filial obrigatória |
| `cliente_filiais` | Escopo de cadastro do cliente, FK composta/índice por filial |
| `veiculo_filiais` | Escopo de cadastro do veículo, FK composta/índice por filial |

Uma linha concede uma capacidade. Cada concessão possui id UUID, usuario_id lógico Auth, alvo local com FK quando aplicável, capacidade allowlist, ativo, concedido_por/em, revogado_por/em e versão não negativa. Constraints recusam autoconcessão, revogação incompleta e datas incoerentes. Índices únicos parciais impedem concessões ativas duplicadas; índices de escopo aceleram EXISTS. Grants administrativos usam unicidade NULLS NOT DISTINCT (PostgreSQL 15+; testes em 17).

**Provisionamento administrativo não está exposto nesta entrega.** Não há endpoint de atribuição/revogação, seed privilegiado, autoatribuição nem concessão global. Tabelas sem concessão implicam negação; uma instalação nova exige mecanismo administrativo confiável futuro. Inserts de fixtures ficam exclusivamente em src/test. Não usar SQL fornecido por usuário final como mecanismo de provisionamento.

Modelo preserva registros revogados. Um futuro mecanismo administrativo deverá conferir delegação/ator atual, motivo/auditoria e usar compare-and-set `WHERE id=? AND version=?`, incrementando version ao revogar. O teste PostgreSQL disputa duas revogações concorrentes com esse protocolo e comprova somente um vencedor; não há API de provisionamento pronta. A versão não impede que um operador privilegiado de banco execute SQL fora do protocolo.

Associações de cadastro são criadas atomicamente no POST autorizado. Não houve backfill automático dos cadastros legados para todas as filiais. Legados sem associação ficam indisponíveis a funcionários até classificação administrativa confiável. Um cadastro de cliente no OS não cria usuário ou vínculo no Auth.

## 6. Consultas paginadas e escopo

`AuthorizedOrderQuery` é porta separada da pesquisa interna preexistente. Seu adapter executa consulta SQL com EXISTS em operational_grants por usuario_id, filial da OS, OS_READ e ativo. Filtros cliente/veículo/filial/status são combinados com AND ao escopo. COUNT e seleção utilizam o mesmo predicado; LIMIT/OFFSET acontecem no PostgreSQL, nunca após filtragem em memória. Sem filtro, o usuário vê apenas suas filiais autorizadas; sem grant, total zero. Ordenação é fixa por created_at DESC e id, sem ordenação arbitrária fornecida pelo consumidor.

Listas de clientes, veículos e filiais partem somente de IDs autorizados obtidos no banco. Não existe busca global seguida de filtragem de OS em memória. O parâmetro filialId dos cadastros apenas escolhe o alvo e sempre é checado no banco; headers e parâmetros usuarioId/sort adicionais não ampliam escopo.

## 7. DTOs e consultas operacionais

Contratos de resposta existentes foram preservados. Os POST de clientes e veículos agora exigem query parameter **filialId**, alteração explícita de contrato necessária porque as entidades legadas não tinham filial de cadastro. Chamadores devem informar uma filial autorizada; não existe default para todas as filiais.

Novos contratos aditivos: OperationalOrderResponse contém id OS, veículo, filial, status e datas de início/fim; OperationalVehicleResponse contém somente placa/marca/modelo/ano. Não incluem clienteId, CPF, contato, finanças, observações livres ou erro Saga. Mecânicos usam exclusivamente essas rotas e não recebem o DTO legado. Não foi removido campo silenciosamente dos DTOs públicos existentes. Atendentes têm acesso ao DTO de cadastro com PII somente para CLIENT_READ nas filiais explícitas; gerentes não recebem esse cadastro. Campos livres em DTOs legados (observações/motivo) devem seguir política de minimização de dados na origem.

## 8. Dependências Auth e limites atuais

O canal HTTP interno de vínculos permanece **desativado** no Auth. O bean produtivo `UnavailableClientAccessAdapter` lança indisponibilidade: não é mock permissivo nem consulta fictícia. Nenhuma configuração desta etapa ativa ClientAccessHttpAdapter/SigV4 automaticamente. O adapter anterior permanece preparado e testado; `AuthenticatedUserToken` agora possui implementação vinculada à autenticação real.

Vínculo positivo/negativo e falha Auth são exercitados em testes de política com doubles explicitamente controlados. Eles não provam ownership distribuído, IAM ou integração implantada. Recursos de cliente reais retornam 503 quando a autorização depende da consulta; pesquisa sem clienteId é negada antes da dependência. Ativação futura exige concluir os gates da 8.3.2-A.1, substituir deliberadamente o bean indisponível e validar o canal confiável completo, sem cache positivo de vínculo.

Papéis de funcionário refletem o JWT até expirar, máximo 900 s. Grants/atribuições são relidos no OS em cada operação e não ficam no JWT. Não há introspecção/Auth DB por requisição nem promessa de revogação instantânea do papel no Auth. Uma revogação de grant concorrente após a decisão pode anteceder o commit de negócio: não foi criado protocolo distribuído/lease nem bloqueio de concessões por toda transação. Novas operações após revogação confirmada observam o banco atual. Relógios precisam estar sincronizados por causa de skew zero.

## 9. Preservação da Saga

OrdemServicoUseCase interno, Orquestrador, SagaEventHandler, SagaEventConsumer, RabbitMessagingConfig, Inbox, Outbox, deduplicação, locks, compensações e contratos de eventos foram preservados. Não foram adicionados @PreAuthorize nesses componentes. Segurança HTTP se encerra na entrada AuthorizedBusinessUseCase; listeners não dependem de CurrentActor nem de SecurityContext. Não há JWT em envelopes, nem reautenticação do usuário durante reentrega.

Teste novo com PostgreSQL/RabbitMQ reais publica diagnostic.start, rejeita JWT HTTP expirado, recebe diagnostic.started/completed no listener e avança a Saga sem identidade HTTP. Reentrega do mesmo evento preserva histórico e deduplicação de Inbox. Suítes anteriores de publisher, falhas/retries/leases, rollback, fluxo/compensações e broker são mantidas na verificação completa.

## 10. Validação e evidências

Testes novos:

- AuthJwtDecoderTest: JWKS HTTP local real, RSA efêmero, assinatura válida/incorreta, algoritmo, none, issuer/audience, sub/jti, formato/nomes/duplicatas de roles, roles vazias, exp/nbf/iat/TTL, limites e fonte JWKS indisponível.
- ResourceAuthorizationTest: papéis/capacidades independentes, atendente/gerente dentro e fora de filial, mecânico atribuído/não atribuído, cliente positivo/negativo/indisponível, ausência de adapter confiável e negação antes de mutação.
- HttpCurrentActorTest: extração exclusivamente de JWT autenticado atual, recusa de outro tipo Authentication e ausência de identidade, sem herança ou scope implícito.
- AuthorizationIntegrationTest: PostgreSQL 17 Testcontainers, Flyway V5, índices/constraints, persistência/revogação/rollback/concorrência, isolamento de usuários, COUNT/paginação, ampliação por filtro/header recusada, JWT RS256 real sobre HTTP, DTO mínimo, cliente fechado e negação sem OS/histórico/Saga/Outbox. RabbitMQ real comprova consumo e deduplicação sem usuário HTTP.
- BusinessApiIntegrationTest preserva cenários anteriores com JWT real e concessões explícitas de teste. OsApplicationTests preserva probes; expectativa anterior de Swagger público foi substituída pela política aprovada.

Não há jwt() substituindo verificação de assinatura. Não foram ignorados testes ou adicionadas exclusões JaCoCo; gate original de 80% permanece.

Execução final em 25/10/2026, 17:15:42 America/Sao_Paulo, Microsoft JDK **21.0.12.1**, Maven **3.10.0**, PostgreSQL/RabbitMQ reais em Testcontainers locais:

| Evidência | Resultado |
| --- | --- |
| `clean verify` | **BUILD SUCCESS**, exit 0, 2 min 11 s; JAR Spring Boot repackaged gerado |
| Suíte completa | **109 testes**, **0 falhas, 0 erros, 0 ignorados**, 24 classes de teste |
| Novos testes | **27 unitários/criptográficos + 8 de integração**; 74 testes anteriores preservados/executados |
| JaCoCo linhas | **1.503/1.618 = 92,89%**, gate original 80% aprovado sem exclusões adicionais |
| Flyway | V1–V5 aplicadas em bancos descartáveis; índices/constraints V5 verificados |
| Mensageria | SagaEventHandlerTest 8, SagaMessageSerializerTest 5, OutboxPublisherTest 2, RabbitMessageBrokerAdapterIntegrationTest 3, testes de persistência/rollback e novo cenário Rabbit real aprovados |
| Git | `diff --check` sem erros; mesmo branch/HEAD; 13 arquivos rastreados alterados + 22 novos; Auth/Execution/Billing com árvores limpas |

Comando executado: distribuição Maven já instalada pelo wrapper, `C:/Users/arneto/.m2/wrapper/dists/apache-maven-3.10.0-bin/39q0qgmngdu329psje7bjgne79/apache-maven-3.10.0/bin/mvn.cmd -B clean verify`, com JAVA_HOME apontando para JDK 21. O lançamento via `mvnw.cmd` encontrou uma falha de inicialização PowerShell do ambiente; não foi alterado o wrapper para contorná-la. Uma primeira compilação no sandbox também falhou ao fechar recursos do javac; a validação completa usou acesso autorizado ao cache Maven, Docker e temporários locais.

Tentativas direcionadas detectaram o flush JPA antes de registrar as FKs de cadastro e uma expectativa antiga de 404 para rota de alteração de status. Foram corrigidos o flush transacional e a expectativa de 403 conforme deny-by-default; nenhum teste foi removido. Todos os cenários passaram na execução final limpa. Logs preexistentes de listeners sem Rabbit nos contextos que não usam broker e reconexões durante encerramento dos containers não correspondem a falhas/ignorados; o cenário novo com broker configurado comprovou consumo real.

Artefatos locais ignorados pelo Git: `target/os-authorization-8.3.2-b-verify.log`, `target/surefire-reports`, `target/site/jacoco/index.html` e `target/site/jacoco/jacoco.xml`. Nenhuma credencial ou dependência AWS real foi necessária. Não se declara integração IAM/ownership implantada a partir desses testes locais.

## 11. Arquivos e pendências

Arquivos de produção alterados: pom.xml, compose.yaml, application.yml, SecurityConfig, quatro controllers, GlobalExceptionHandler, OrdemServicoJpaRepository e OrdemServicoRepositoryAdapter. Criados: pacote application/authorization (Actor, CurrentActor, Capability, AuthorizationDenied, OperationalAccessPort, AuthorizedOrderQuery, ResourceAuthorization, AuthorizedBusinessUseCase); security/AuthJwtDecoder e HttpCurrentActor; identity/UnavailableClientAccessAdapter; persistence/OperationalAccessAdapter; OperationalOrderResponse/OperationalVehicleResponse; migração V5. Testes/documentação/configuração exclusiva de testes constam na seção anterior e no diff Git.

Pendências explícitas: canal IAM/SigV4 confiável no Auth e testes distribuídos; provisionamento/revogação administrativa com autorização/auditoria próprias; classificação de cadastros legados por filial; política de frescor/revogação de papéis além do TTL; observabilidade sanitizada e retenção/auditoria operacional; revisão dos campos livres em respostas legadas. Não publicar Swagger/Saga sem concessões específicas. Falha ou ausência de dependência jamais pode gerar grant substituto.

Não houve deploy, Terraform, AWS, alteração de workflows, segundo orquestrador ou modificação de Execution/Billing/Auth. Próxima ação desta entrega é revisar o diff e as configurações/grants necessários; a 8.3.3 exige solicitação separada.

## 12. Complemento — atendimento entre filiais (26/10/2026)

### Contratos aditivos e política de legitimidade

Foram acrescentados `PUT /api/v1/clientes/{clienteId}/filiais/{filialId}` e `PUT /api/v1/veiculos/{veiculoId}/filiais/{filialId}`. O caminho define o destino. Corpo do cliente: `{"filialOrigemId":"<uuid>","motivo":"Atendimento no destino"}`. Corpo do veículo: `{"clienteId":"<uuid>","filialOrigemId":"<uuid>","motivo":"Atendimento no destino"}`. Motivo obrigatório, até 200 caracteres; não inserir PII no motivo. Sucesso e repetição retornam 204 sem corpo, sem cadastro integral ou indicação externa de criação/repetição.

A política local de legitimidade combina **visibilidade prévia do recurso na origem** com **delegação explícita de associação no destino**. ATENDENTE precisa de CLIENT_READ na origem, associação prévia cliente-origem e CLIENT_ASSOCIATE no destino. Para veículo, exige adicionalmente VEHICLE_READ e veículo classificado na mesma origem, VEHICLE_ASSOCIATE no destino, proprietário correspondente ao cliente informado e cliente já associado ao destino. As concessões são relidas do banco para o UUID derivado exclusivamente do JWT autenticado. IDs do corpo/caminho selecionam alvos; não constituem evidência de autorização. CREATE não substitui ASSOCIATE e nenhuma operação concede papéis ou grants ao chamador.

Essa política permite apenas ao funcionário já autorizado a conhecer o cadastro compartilhar esse cadastro com uma filial na qual recebeu expressamente essa capacidade. A identificação continua pelos GETs existentes e restritos ao escopo. Não foi acrescentada busca global por CPF, placa ou UUID, nem descoberta automática de existência em outra filial. Conhecer um identificador sem acesso à origem não autoriza associação. No caso de duas equipes sem funcionário com ambas as delegações, o handoff permanece indisponível: seria necessário fluxo confiável próprio e não se presume consentimento de cliente ou autorização global. O canal Auth de vínculos continua fechado e não é simulado para viabilizar esse fluxo operacional de funcionários.

UUID inexistente, cadastro de origem não autorizado ou legado sem classificação recebem 403 genérico antes de resolver estado ou retornar dados. Papel incorreto recebe 403; Bearer ausente/inválido, 401. Cliente/filiais inativos já no escopo autorizado geram 409. Origem igual ao destino mantém todas as exigências e é idempotente. Veículo não tem estado ativo próprio no modelo atual: valida-se existência, seu cliente ativo e pertencimento, sem inventar atributo de domínio.

### Persistência, auditoria e concorrência

`BranchAssociationUseCase` é entrada HTTP transacional independente dos consumidores internos. Depende de CurrentActor/ResourceAuthorization/OperationalAccessPort e da nova porta BranchAssociationPort. O adapter JDBC é MANDATORY: suas operações precisam da transação da aplicação. Consulta dados mínimos e aplica FOR SHARE em cliente, filiais e veículo, evitando alteração concorrente de estado/proprietário durante a associação. Não modifica essas entidades. As concessões seguem a política de frescor e a janela entre decisão/revogação já documentadas na seção 8.

As PKs compostas V5 e `INSERT ... ON CONFLICT (...) DO NOTHING` asseguram um vínculo por recurso/destino, inclusive entre transações concorrentes. Associações anteriores não são removidas. Cliente e veículo são associados por operações separadas; a associação do cliente deve ser confirmada antes da associação do veículo.

Cada PUT aceito insere branch_association_audit na mesma transação: id da operação, usuario_id real, cliente/veículo, origem, destino, operação, motivo, occurred_at e created. Repetições também geram auditoria com created=false; idempotência refere-se ao vínculo de negócio, sem suprimir registros de auditoria das tentativas aceitas. Não há endpoint público de auditoria nem alteração/exclusão desses registros na aplicação. Falha no insert de auditoria reverte o vínculo, nunca retorna sucesso parcial. Tentativas negadas não são registradas como sucesso nessa tabela. Retenção e acesso administrativo à auditoria dependem de política operacional futura, sem expô-la por Swagger ou Saga.

V6 amplia apenas a allowlist da constraint de capacidades de operational_grants e cria branch_association_audit com FKs, checks e índices. Não atribui capacidades a ninguém. V5 permanece com SHA-256 `615db5b4063f87b6b48cf8be4921e7b0384e72ea40ba9c3427643d06c7340a28`; nenhuma migração original foi reescrita e nenhum backfill universal foi criado.

### Compatibilidade e dados legados

POST clientes/veículos mantém filialId explícito na query e os mesmos JSON/DTOs. POST OS mantém seu JSON, validação de escopo, histórico e Saga. GETs e consultas paginadas não foram alterados: associar cadastro a B não transfere OS de A nem concede OS_READ/OS_OPEN. Uma nova OS em B utiliza os mesmos clienteId/veiculoId após as duas associações legítimas. A OS histórica, histórico, Saga, Outbox e Inbox de A permanecem intactos. O único ajuste em SecurityConfig é a regra PUT das duas rotas novas; decoder JWT, JWKS, extração de identidade, RabbitMQ e contratos de eventos não foram modificados.

`PopulatedMigrationIntegrationTest` aplica V1–V4, insere registros reais de cliente/filial/veículo/OS/histórico/Saga/Outbox/Inbox e fotografa todas as colunas em JSON. Aplica V5, executa validate e compara os registros integralmente. Verifica ausência de concessões/associações e FKs. Depois aplica V6, valida novamente, verifica nova execução sem migrações pendentes e preservação integral dos registros/filial histórica, constraints e tabela de auditoria inicialmente vazia.

Cadastros legados sem classificação não ganham filial automaticamente. São recusados pelas novas operações, pois não existe origem autorizada comprovável. Classificação inicial, identificação global, consentimento remoto e handoff entre equipes continuam bloqueados até mecanismo confiável específico. Não há endpoint público de bootstrap ou provisionamento. README registra os novos contratos, configuração Bearer e preparação E2E por fixtures isoladas ou processo administrativo confiável, sem seeds privilegiados.

### Testes e arquivos deste complemento

| Requisitos | Evidência |
| --- | --- |
| Cliente/veículo em duas filiais, abertura posterior e histórico original | BranchAssociationIntegrationTest.associatesExistingRecordsOpensNewOsAndKeepsHistoryAndPaginationScoped |
| Repetição e auditoria sem duplicar vínculo | repeatedRequestsKeepOneAssociationAndAuditEachAcceptedOperation |
| Duas associações concorrentes de cliente e veículo | concurrentRequestsCreateOnlyOneAssociationOfEachType |
| Capacidade destino específica; CREATE insuficiente | destinationNeedsAssociationCapabilityAndNeverAcceptsCreateAsSubstitute |
| Papel e autenticação | rejectsMissingJwtAndWrongRoles |
| UUID inexistente, tentativa de enumeração e legados não classificados | unknownHiddenAndLegacyIdsAreDeniedUniformlyWithoutLookupOrPii |
| Proprietário diferente e ordem cliente→veículo | rejectsOwnerMismatchAndRequiresClientAssociationAtDestinationFirst |
| Origem autorizada, estado e motivo | requiresOriginReadAndActiveResourcesAndValidReason |
| Falha de auditoria/rollback | auditFailureRollsBackAssociation |
| Negação antes da persistência e validação fora do HTTP | BranchAssociationUseCaseTest |
| Upgrade V4 populada → V5 → V6 e constraints | PopulatedMigrationIntegrationTest |
| Paginação e Saga sem identidade HTTP | Novos testes de abertura/contagem e AuthorizationIntegrationTest.realRabbitConsumerAdvancesSagaAndDeduplicatesWithoutHttpIdentity, mais suítes anteriores preservadas |

Arquivos acrescentados: BranchAssociationUseCase/BranchAssociationPort, BranchAssociationAdapter, BranchAssociationController, AssociateClientRequest/AssociateVehicleRequest, V6, BranchAssociationIntegrationTest, BranchAssociationUseCaseTest e PopulatedMigrationIntegrationTest. Alterados neste complemento: Capability, uma regra HTTP em SecurityConfig, README e este documento. São 10 arquivos novos e 4 ajustados neste complemento; as alterações locais anteriores da 8.3.2-B foram preservadas.

Execução final em **26/10/2026, 09:23:24 America/Sao_Paulo**, Microsoft JDK 21.0.12.1 e Maven 3.10.0 local:

| Evidência final do complemento | Resultado |
| --- | --- |
| `mvn.cmd -B clean verify` | **BUILD SUCCESS**, exit 0, 3 min 08 s; JAR repackaged gerado |
| Suíte completa | **121 testes**, **0 falhas, 0 erros, 0 ignorados**, 27 classes |
| Novos cenários | **12**: 9 HTTP/PostgreSQL, 1 upgrade PostgreSQL e 2 unitários; 109 anteriores preservados |
| JaCoCo linhas | **1.563/1.678 = 93,15%**, acima dos 92,89% anteriores; gate 80% sem novas exclusões |
| Dados antigos | V4 populada → V5 → V6 com migrate/validate aprovados, oito tabelas de negócio/mensageria preservadas integralmente, sem associações ou concessões automáticas |
| Saga/mensageria | Suítes anteriores e cenário RabbitMQ real sem identidade HTTP aprovados; nenhuma mudança no código/contratos |
| V5 | SHA-256 preservado; V1–V4 sem diferenças contra HEAD |
| Git | `homolog`, HEAD `6b3e11f77f873068d1e6b1e68f71586bd67adea9`, sem commit/push; árvore acumulada com 14 arquivos rastreados modificados e 32 novos; diff --check sem erros |

Comando usa a mesma distribuição Maven local descrita na seção 10, JAVA_HOME JDK 21 e Docker Desktop para Testcontainers, sem credenciais AWS reais. Log final: `target/os-branch-association-verify.log`; relatórios em `target/surefire-reports` e `target/site/jacoco`. Testes direcionados de associação (9) e upgrade/política (3) também passaram antes da execução completa. Nenhum teste foi removido ou ignorado. Auth/Execution/Billing permanecem sem alterações. Não houve deploy, Terraform, mudança de workflows ou início da 8.3.3.
