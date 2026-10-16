package br.com.financesflow.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CreditCardIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt-secret", () ->
                "integration-test-secret-key-at-least-32-characters");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void userCanPayAnInstallmentInvoiceAndReleaseOnlyThatPartOfTheLimit() throws Exception {
        // Register and log in through the real HTTP endpoints.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Integration User","email":"integration@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated());

        var login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"integration@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        var token = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
        var authorization = "Bearer " + token;

        // Create the card and a R$ 300 purchase in 3 installments.
        var cardResult = mockMvc.perform(post("/api/cards")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Integration Card","creditLimit":2500.00,"closingDay":10,"dueDay":17}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        var cardId = objectMapper.readTree(cardResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/cards/{cardId}/purchases", cardId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Installment test","category":"Test","amount":300.00,"purchaseDate":"2026-10-10","installments":3}
                                """))
                .andExpect(status().isCreated());

        // November gets one R$ 100 installment. Paying it leaves R$ 200 in future installments.
        mockMvc.perform(get("/api/cards/{cardId}/invoices", cardId)
                        .param("month", "2026-11")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(100.0))
                .andExpect(jsonPath("$.paid").value(false));

        mockMvc.perform(post("/api/cards/{cardId}/invoices/pay", cardId)
                        .param("month", "2026-11")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.0));

        mockMvc.perform(get("/api/cards")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableLimit").value(2300.0));

        mockMvc.perform(post("/api/cards/{cardId}/invoices/pay", cardId)
                        .param("month", "2026-11")
                        .header("Authorization", authorization))
                .andExpect(status().isConflict());
    }

    @Test
    void userCannotSeeAnotherUsersCardOrInvoice() throws Exception {
        // Arrange: duas contas independentes, cada uma com seu próprio cartão.
        var ownerAToken = registerAndLogin("owner-a@example.com", "Owner A");
        var ownerBToken = registerAndLogin("owner-b@example.com", "Owner B");
        var ownerACardId = createCard(ownerAToken, "Card A");
        var ownerBCardId = createCard(ownerBToken, "Card B");

        // Act + Assert: mesmo conhecendo o ID, B não pode consultar a fatura de A.
        mockMvc.perform(get("/api/cards/{cardId}/invoices", ownerACardId)
                        .param("month", "2026-11")
                        .header("Authorization", "Bearer " + ownerBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Cartão não encontrado"));

        // A listagem autenticada de B também deve conter apenas o cartão de B.
        mockMvc.perform(get("/api/cards")
                        .header("Authorization", "Bearer " + ownerBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ownerBCardId));
    }

    @Test
    void creatingCardWithZeroLimitReturnsBadRequestAndDoesNotSaveCard() throws Exception {
        var token = registerAndLogin("invalid-card@example.com", "Invalid Card User");

        mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalid Card","creditLimit":0,"closingDay":10,"dueDay":17}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Um ou mais campos são inválidos."))
                .andExpect(jsonPath("$.errors[0].field").value("creditLimit"));

        mockMvc.perform(get("/api/cards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void protectedCardEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Autenticação ausente ou inválida."));

        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Token Card","creditLimit":1000.00,"closingDay":10,"dueDay":17}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void openApiDocumentsProblemDetailsAndExpectedErrorStatuses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ApiProblem.properties.status.type")
                        .value("integer"))
                .andExpect(jsonPath("$.paths['/api/cards'].post.responses['400'].description")
                        .value("Requisição inválida ou campos fora das regras de validação."))
                .andExpect(jsonPath("$.paths['/api/cards'].get.responses['401'].description")
                        .value("Token ausente, inválido ou expirado."));
    }

    private String registerAndLogin(String email, String name) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("name", name, "email", email, "password", "password123"))))
                .andExpect(status().isCreated());

        var login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", email, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String createCard(String token, String name) throws Exception {
        var response = mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", name, "creditLimit", 1000.00, "closingDay", 10, "dueDay", 17))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString())
                .get("id").asText();
    }
}
