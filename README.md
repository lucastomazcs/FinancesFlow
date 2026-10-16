# FinancesFlow API

API REST para gestão financeira pessoal, construída com Java 21 e Spring Boot.

## Requisitos

- JDK 21
- Maven 3.6.3 ou superior
- Docker Desktop com Docker Compose

## Executar com Docker Compose

Com o Docker Desktop ativo, inicie o banco e a API:

```bash
docker compose up -d --build
```

O Swagger fica em `http://localhost:8080/swagger-ui/index.html`; o PostgreSQL continua acessível na porta `5433`. O Compose espera o banco ficar saudável antes de iniciar a API. Os dados permanecem no volume `financesflow-postgres-data` quando os containers são parados com `docker compose down`.

Para desenvolver sem Docker para a API, inicie somente o banco com `docker compose up -d database` e rode `mvn spring-boot:run`. No PowerShell, configure antes `DB_URL=jdbc:postgresql://localhost:5433/financesflow`, `DB_USERNAME=financesflow`, `DB_PASSWORD=financesflow` e um `JWT_SECRET` com ao menos 32 caracteres.

Na primeira inicialização, o Flyway cria as tabelas do projeto. O segredo JWT padrão do Compose é somente para desenvolvimento local; configure `JWT_SECRET` no ambiente ou no arquivo `.env` antes de usar em outro ambiente.

## Testes

Execute `mvn test` com o Docker Desktop ativo. Os testes unitários usam mocks; os testes de integração iniciam um PostgreSQL temporário com Testcontainers e percorrem os fluxos HTTP sem usar o banco de desenvolvimento.

## Integração contínua

O GitHub Actions executa verificações em cada push e pull request. O job do backend roda `mvn test` com Java 21 e usa Docker para os testes de integração com Testcontainers. Em paralelo, o job do frontend instala as versões registradas em `frontend/package-lock.json` com `npm ci` e roda `npm run build`.

Um workflow verde confirma que os testes do backend passaram e que o frontend compila. Para ver o resultado na aba **Actions**, envie este projeto para um repositório GitHub.

## Interface web

Com a API e o banco ativos pelo Compose, inicie o front-end em outro terminal:

```bash
cd frontend
npm install
npm run dev
```

Abra `http://localhost:5173`. O Vite encaminha as chamadas `/api` para a API em `http://localhost:8080`. A interface inicial inclui cadastro/login, resumo mensal e criação/listagem de transações.

## Endpoints disponíveis

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `POST` | `/api/transactions` | Cria uma transação |
| `GET` | `/api/transactions` | Lista transações |
| `GET` | `/api/transactions/{id}` | Busca uma transação |
| `PUT` | `/api/transactions/{id}` | Atualiza uma transação |
| `DELETE` | `/api/transactions/{id}` | Remove uma transação |
| `POST` | `/api/auth/register` | Cadastra um usuário |
| `POST` | `/api/auth/login` | Autentica e retorna um token JWT |
| `GET` | `/api/reports/monthly?month=2026-10` | Gera o resumo mensal do usuário autenticado |
| `POST` | `/api/budgets` | Cria um orçamento mensal para uma categoria |
| `GET` | `/api/budgets?month=2026-10` | Lista os orçamentos e gastos do mês |
| `POST` | `/api/cards` | Cadastra um cartão de crédito |
| `GET` | `/api/cards` | Lista os cartões do usuário autenticado |
| `POST` | `/api/cards/{cardId}/purchases` | Registra uma compra no cartão |
| `GET` | `/api/cards/{cardId}/invoices?month=2026-11` | Consulta uma fatura mensal do cartão |
| `POST` | `/api/cards/{cardId}/invoices/pay?month=2026-11` | Paga uma fatura e libera o limite correspondente |

As rotas de transações, relatórios e orçamentos exigem o token retornado pelo login. No Swagger, clique em **Authorize** e informe o token. Cada consulta considera apenas o usuário autenticado.

Erros da API usam o formato `application/problem+json` (Problem Details), com `type`, `title`, `status`, `detail` e `instance`. Erros de validação também incluem `errors`, uma lista com o campo e a mensagem de cada problema.

Exemplo de corpo para criar uma transação:

```json
{
  "description": "Mercado do mês",
  "amount": 450.75,
  "type": "EXPENSE",
  "category": "Alimentação",
  "transactionDate": "2026-10-15"
}
```

Os tipos aceitos são `INCOME` e `EXPENSE`. Valores precisam ser positivos.

Exemplo de corpo para criar um orçamento:

```json
{
  "category": "Alimentação",
  "monthlyLimit": 800.00,
  "month": "2026-10"
}
```

O orçamento agrega transações do tipo `EXPENSE` pela data da transação e parcelas de compras no cartão pela fatura a que pertencem. Há um orçamento por categoria e mês para cada usuário. O campo `month` usa o formato `AAAA-MM`.

Exemplo de corpo para cadastrar um cartão:

```json
{
  "name": "Cartão principal",
  "creditLimit": 2500.00,
  "closingDay": 10,
  "dueDay": 17
}
```

O cartão pertence ao usuário autenticado e não armazena o número completo. Os dias de fechamento e vencimento aceitam valores de 1 a 28, e o vencimento deve ser posterior ao fechamento. Compras feitas no dia de fechamento ou depois vão para a fatura do mês seguinte. Por exemplo, com fechamento no dia 10, uma compra em 10/10 e outra em 11/10 entram na fatura de novembro. Relatórios mensais e orçamentos contam o valor de cada parcela no mês da fatura.

O corpo de compra aceita `installments` de 1 a 48. O limite disponível diminui pelo valor total da compra; a fatura e o relatório mensal recebem uma parcela por mês. Se o valor não puder ser dividido exatamente em centavos, a última parcela recebe o ajuste de arredondamento.

Para registrar o pagamento, use `POST /api/cards/{cardId}/invoices/pay?month=AAAA-MM`. Uma fatura vazia ou já paga retorna `409 Conflict`. O pagamento libera do limite somente as parcelas daquela fatura; as parcelas futuras continuam comprometendo o limite.

## Códigos HTTP úteis

- `201 Created`: usuário, transação ou orçamento criado.
- `200 OK`: consulta, login ou atualização concluída.
- `400 Bad Request`: corpo ou parâmetro inválido.
- `401 Unauthorized`: token ausente ou inválido.
- `404 Not Found`: registro não encontrado para o usuário autenticado.
- `409 Conflict`: e-mail já cadastrado, orçamento duplicado para a categoria e mês ou cartão duplicado.
