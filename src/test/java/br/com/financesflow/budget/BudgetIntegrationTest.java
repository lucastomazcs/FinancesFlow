package br.com.financesflow.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
class BudgetIntegrationTest {

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
    void budgetHttpFlowPersistsDataAndReflectsExpenseSpending() throws Exception {
        // Register and authenticate through the same HTTP endpoints used by the application.
        var token = registerAndLogin("budget-flow@example.com", "Budget Flow");
        var authorization = "Bearer " + token;

        // Create a R$ 50 budget and a R$ 65 expense for that category and month.
        mockMvc.perform(post("/api/budgets")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"Mercado","monthlyLimit":50.00,"month":"2026-11"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("Mercado"))
                .andExpect(jsonPath("$.spent").value(0.0));

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Compra do mês","amount":65.00,"type":"EXPENSE","category":"Mercado","transactionDate":"2026-11-15"}
                                """))
                .andExpect(status().isCreated());

        // Reading the budget now returns values recalculated from the persisted transaction.
        mockMvc.perform(get("/api/budgets")
                        .param("month", "2026-11")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spent").value(65.0))
                .andExpect(jsonPath("$[0].remaining").value(-15.0))
                .andExpect(jsonPath("$[0].overLimit").value(true));

        // The same category and month cannot be registered twice for this account.
        mockMvc.perform(post("/api/budgets")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"  mercado ","monthlyLimit":100.00,"month":"2026-11"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void budgetListIsIsolatedPerAuthenticatedUser() throws Exception {
        // Create two independent accounts and one budget for each.
        var ownerAToken = registerAndLogin("budget-owner-a@example.com", "Budget Owner A");
        var ownerBToken = registerAndLogin("budget-owner-b@example.com", "Budget Owner B");
        createBudget(ownerAToken, "A only", "2026-11");
        createBudget(ownerBToken, "B only", "2026-11");

        // B sees only B's budget, although both budgets use the same month.
        mockMvc.perform(get("/api/budgets")
                        .param("month", "2026-11")
                        .header("Authorization", "Bearer " + ownerBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("B only"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void budgetEndpointsRequireAuthenticationAndValidatePositiveLimit() throws Exception {
        mockMvc.perform(get("/api/budgets").param("month", "2026-11"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));

        var token = registerAndLogin("budget-invalid@example.com", "Invalid Budget");
        mockMvc.perform(post("/api/budgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"Mercado","monthlyLimit":0,"month":"2026-11"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Um ou mais campos são inválidos."));
    }

    private String registerAndLogin(String email, String name) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "email", email, "password", "password123"))))
                .andExpect(status().isCreated());

        var login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private void createBudget(String token, String category, String month) throws Exception {
        mockMvc.perform(post("/api/budgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", category, "monthlyLimit", 100.0, "month", month))))
                .andExpect(status().isCreated());
    }
}
