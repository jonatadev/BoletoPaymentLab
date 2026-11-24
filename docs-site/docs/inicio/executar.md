---
title: Executar o laboratório
---

## Pré-requisitos

Use JDK 25, Docker com Compose e o Gradle Wrapper do repositório. O portal
usa Node.js 22 ou superior e pode rodar separadamente do backend.
Para gerar os certificados locais do Kafka, use Python 3 e OpenSSL.

## Subir a infraestrutura

Na raiz do projeto, copie `.env.example` para `.env` somente se o arquivo
ainda não existir. Confira as variáveis PostgreSQL e a porta escolhida.
Para iniciar o Keycloak, use credenciais administrativas de desenvolvimento
definidas no próprio `.env` antes da primeira inicialização.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
python infra/kafka/generate-local.py
docker compose config --quiet
docker compose up -d
docker compose ps
```

A página de [Docker Compose](../operacao/docker.md) lista serviços,
variáveis e verificações. Se precisar apenas do banco, execute
`docker compose up -d postgres`.

## Iniciar o Spring Boot

1. Importe o projeto Gradle no IntelliJ usando o Wrapper e selecione JDK 25.
2. Se necessário, copie `src/main/resources/application-local.example.yaml`
   para `application-local.yaml`. O perfil local fica fora do Git.
3. Disponibilize `BPL_DB_USERNAME` e `BPL_DB_PASSWORD` na configuração de
   execução se o YAML usa os placeholders. Ajuste a URL JDBC para a porta e
   banco do seu Compose. O arquivo de exemplo usa a porta `5434`, e o
   `.env.example` começa com `5433`.
4. Execute `BoletoPaymentLabApplication` com
   `--spring.profiles.active=local,platform`. O perfil `platform` habilita
   publicação Kafka e exportação de traces. Configure
   `SPRING_CONFIG_IMPORT=file:./.kafka-secrets/local/app/spring.yaml` para
   autenticar no Kafka. O perfil `local` sozinho mantém os eventos no Outbox
   por padrão. Consulte [Segurança do Kafka](../operacao/kafka-seguro.md)
   caso precise ajustar a CA ou o endereço do broker.
5. Confirme `GET /actuator/health/readiness` no endereço da API
   `http://127.0.0.1:8080`. A prontidão inclui a conexão com o banco.

Se as variáveis Spring já estiverem exportadas, também é possível executar
pela linha de comando:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local,platform"
```

## Iniciar a imagem Docker

A imagem local usa o nome `boleto-payment-lab:local`. O Docker exige nomes
de repositório em minúsculas, enquanto a identidade da aplicação permanece
`BoletoPaymentLab`.

```powershell
docker compose --profile app build boleto-payment-lab
docker compose --profile app up -d boleto-payment-lab
docker compose --profile app ps boleto-payment-lab
```

## Usar o Postman

Importe os dois arquivos JSON em `postman/` e selecione **Boleto Payment Lab
Local**. Configure apenas os valores locais dos segredos de cliente.
Execute **Get token** para cada cliente necessário. Emita um boleto antes
das consultas e dos replays, pois o teste inicial salva seu UUID. Use boletos
diferentes para testar pagamento e cancelamento.

Consulte [Segurança](../operacao/seguranca.md) para obter tokens e a
[API REST](../referencia/api.md) para replicar as chamadas manualmente.
