# CI acadêmico do OS Service

## A. Arquivos modificados

- `.github/workflows/ci.yml`: etapas obrigatórias simplificadas e permissões mínimas.
- `scripts/ci/verify.py`: segredos básicos, Maven/JaCoCo, build e runtime; removidos snapshots, recibos, exportação de camadas e scanners de JAR/binários.
- `scripts/ci/test_verify.py`: 15 testes dos gates e relatórios, incluindo cenários de falha.
- `compose.yaml`: as duas senhas PostgreSQL usam `${DB_PASSWORD:-oficina_os}`, preservando o valor padrão local e permitindo configuração por ambiente.
- `README.md`, este documento e `docs/reports/ci-os-service.json`: escopo e resultados atuais. Documentos anteriores receberam somente indicação de que são históricos.

Dockerfile, POM e fontes Java preparados anteriormente foram preservados. A proposta GnuTLS permanece arquivada, sem aplicação na Infra ou no CI.

## B. Workflow final

`Productive build verification` executa em PR/push para `homolog` e via `workflow_dispatch`, em `ubuntu-latest`, com Java 21 e `contents: read`. Ações externas continuam fixadas por SHA completo.

Gates obrigatórios:

1. Checkout da revisão do evento e configuração Java 21.
2. Testes do integrador Python e verificação básica de segredos.
3. `sh mvnw -B -ntp clean verify` no Linux; `mvnw.cmd` no Windows.
4. Relatórios Maven com testes executados, zero falhas/erros/skips e JaCoCo LINE >= 80%.
5. `docker build --platform=linux/amd64`, com verificação de plataforma, UID, entrypoint, health check e SIGTERM.
6. Inicialização da imagem com PostgreSQL e RabbitMQ temporários, endpoints de saúde, Java 21, UID 10001 e encerramento com SIGTERM.

O wrapper é invocado por `sh` porque o arquivo `mvnw` está versionado sem permissão executável. O workflow fornece as dependências de runtime por containers locais; indisponibilidade ou falha de inicialização reprova o job.

Somente coleta e upload de JSON sanitizado usam `if: always()`. Falhas anteriores continuam falhas; não há `continue-on-error`. Saídas brutas dos comandos ficam capturadas, sem imprimir segredos ou ambiente. O relatório registra revisão, etapa, checks, testes/cobertura, imagem e códigos de falha.

Não há credenciais AWS, publicação de imagem, ECR/S3, Terraform, kubectl, Lambda ou deploy. `upload-artifact` envia apenas os relatórios do próprio GitHub.

## C. Testes e cobertura

Nesta alteração foram executados **15 testes do CI**, com sucesso. Eles verificam falhas de Maven, Docker, cobertura, scanner e runtime, bem como a preservação dessas falhas na coleta de relatórios.

Os relatórios Maven existentes foram relidos pelo parser atual: **155 testes, zero falhas/erros/skips; 1620 linhas cobertas e 134 não cobertas, LINE 92,3603%**. Maven não foi repetido nesta etapa: POM e fontes Java não mudaram, e o Dockerfile também permanece idêntico. Essa evidência é explicitamente reutilizada, não apresentada como nova execução da suíte.

Cada execução futura do workflow roda `clean verify`; não reutiliza relatórios antigos para aprovar o gate.

## D. Docker e runtime

Novo comando de build validado localmente, com cache e sem publicação: `oficina-os-ci:academic-local`, linux/amd64. O build continua compilando as próprias fontes, sem copiar `target` do host.

Runtime repetido na imagem recém-construída: Java 21, UID 10001, health/liveness/readiness em `UP` e SIGTERM com saída 143. PostgreSQL/RabbitMQ foram fornecidos em rede interna; os recursos temporários do teste foram removidos. Dockerfile, digests das bases, entrypoint e health check foram preservados.

## E. Verificação básica de segurança

Reutiliza-se somente a guarda de fontes `check_secret_files.py`, obtida da Infra no SHA `02f08249276f9148fbbb1305a321d7056f3d9c0b`. O módulo central não foi alterado. São inspecionados arquivos Git, incluindo não rastreados e respeitando ignores; `target`, caches e ferramentas locais não entram no escopo.

Continuam bloqueados arquivos sensíveis, material de chave privada nas fontes, `.env`, Access Key IDs desconhecidos e JWTs. O complemento local detecta tokens GitHub e valores literais de senha/token/chave em configurações comuns, sem imprimir valores. Referências de ambiente e valores vazios são aceitos; não há nova allowlist de bibliotecas.

A inspeção profunda de dependências JAR e de camadas Docker foi removida do pipeline. O relatório atual registra `third_party_binary_scan.status: not_run` e `required: false`. Os achados históricos não foram convertidos em aprovação. Esta decisão não declara os binários livres de CVEs nem aprova uma release completa.

## F. Bloqueadores reais restantes

Nenhum bloqueador local identificado nos checks do escopo atual. A execução remota não foi realizada: GitHub Actions permanece desabilitado por decisão existente. Uma execução futura depende da disponibilidade de Docker, das imagens/dependências públicas e do checkout da guarda central. Nenhuma dessas configurações administrativas foi alterada.

YAML, sintaxe Python, testes dos scripts, scanner básico real, build, runtime e `git diff --check` foram verificados localmente. O driver completo não foi executado via GitHub Actions; os gates e sua orquestração foram validados localmente por testes e comandos reais, com reutilização explícita da evidência Maven descrita acima.

## G. Estado Git

Repositório `AuroNet0/tech-challenge-oficina-os-service`, branch `homolog`, HEAD `9391d3b9d4a5949a2c6447fc01becbb87d45142e`. Alterações locais anteriores preservadas; árvore modificada para revisão. Nenhum staging, commit ou push.

Infra, Auth, Execution e Billing não foram modificados. Lógica de negócio, Saga, eventos, autenticação e persistência permanecem inalterados. Não houve operação AWS, Kubernetes remoto, publicação ou deploy.

## H. Recomendação de commit

Pronto para revisão do escopo acadêmico. Mensagem sugerida: `ci(os): simplify academic build and verification gates`. Revisar o diff e os relatórios antes de qualquer commit; nenhuma operação Git de publicação foi executada.
