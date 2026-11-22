package com.finpulse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpulse.dto.CreateBudgetRequest;
import com.finpulse.dto.CreateTransactionRequest;
import com.finpulse.dto.RegisterRequest;
import com.finpulse.dto.UpdateBudgetRequest;
import com.finpulse.entity.Category;
import com.finpulse.entity.TransactionType;
import com.finpulse.entity.User;
import com.finpulse.repository.BudgetRepository;
import com.finpulse.repository.CategoryRepository;
import com.finpulse.repository.RefreshTokenRepository;
import com.finpulse.repository.TransactionRepository;
import com.finpulse.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private Category incomeCategory;
    private Category foodCategory;
    private Category transportCategory;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        incomeCategory = categoryRepository.save(
                new Category(null, "Gaji", TransactionType.INCOME, "wallet", "#10B981", true)
        );
        foodCategory = categoryRepository.save(
                new Category(null, "Makanan & Minuman", TransactionType.EXPENSE, "utensils", "#F59E0B", true)
        );
        transportCategory = categoryRepository.save(
                new Category(null, "Transportasi", TransactionType.EXPENSE, "car", "#3B82F6", true)
        );
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        transactionRepository.deleteAllInBatch();
        budgetRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void crudBudgetsAndSpendingCalculationsShouldBeAccurate() throws Exception {
        String token = registerAndGetToken("Budi", "budi@finpulse.app");

        // November 2026 expenses for Food: 1,000,000 + 750,000 = 1,750,000
        createTransaction(token, foodCategory.getId(), TransactionType.EXPENSE, "1000000", "2026-11-01");
        createTransaction(token, foodCategory.getId(), TransactionType.EXPENSE, "750000", "2026-11-30");
        // Outside November (October & December) must be ignored for November budget
        createTransaction(token, foodCategory.getId(), TransactionType.EXPENSE, "500000", "2026-10-31");
        createTransaction(token, foodCategory.getId(), TransactionType.EXPENSE, "500000", "2026-12-01");
        // Transport expense in November: 600,000
        createTransaction(token, transportCategory.getId(), TransactionType.EXPENSE, "600000", "2026-11-15");

        // Create Food budget limit 2,500,000 -> spent 1,750,000, remaining 750,000, utilization 70.0, over=false
        CreateBudgetRequest foodBudgetReq = new CreateBudgetRequest(
                foodCategory.getId(), 11, 2026, new BigDecimal("2500000.00")
        );
        MvcResult createResult = mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(foodBudgetReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(foodCategory.getId().toString()))
                .andExpect(jsonPath("$.categoryName").value("Makanan & Minuman"))
                .andExpect(jsonPath("$.month").value(11))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.amountLimit").value(2500000.0))
                .andExpect(jsonPath("$.spentAmount").value(1750000.0))
                .andExpect(jsonPath("$.remainingAmount").value(750000.0))
                .andExpect(jsonPath("$.utilizationPercentage").value(70.0))
                .andExpect(jsonPath("$.isOverBudget").value(false))
                .andReturn();

        String foodBudgetId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // Create Transport budget limit 500,000 (spent 600,000 -> over budget 120.0%)
        CreateBudgetRequest transportBudgetReq = new CreateBudgetRequest(
                transportCategory.getId(), 11, 2026, new BigDecimal("500000.00")
        );
        mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transportBudgetReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spentAmount").value(600000.0))
                .andExpect(jsonPath("$.remainingAmount").value(-100000.0))
                .andExpect(jsonPath("$.utilizationPercentage").value(120.0))
                .andExpect(jsonPath("$.isOverBudget").value(true));

        // GET list for Nov 2026
        mockMvc.perform(get("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "11")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].categoryName").value("Makanan & Minuman"))
                .andExpect(jsonPath("$[0].isOverBudget").value(false))
                .andExpect(jsonPath("$[1].categoryName").value("Transportasi"))
                .andExpect(jsonPath("$[1].isOverBudget").value(true));

        // PUT update Food budget limit to 1,500,000 -> now over budget (116.67%)
        mockMvc.perform(put("/api/budgets/{id}", foodBudgetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBudgetRequest(new BigDecimal("1500000.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountLimit").value(1500000.0))
                .andExpect(jsonPath("$.spentAmount").value(1750000.0))
                .andExpect(jsonPath("$.remainingAmount").value(-250000.0))
                .andExpect(jsonPath("$.utilizationPercentage").value(116.67))
                .andExpect(jsonPath("$.isOverBudget").value(true));

        // DELETE Food budget -> 204
        mockMvc.perform(delete("/api/budgets/{id}", foodBudgetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "11")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void budgetValidationDuplicateAndPeriodRulesShouldBeEnforced() throws Exception {
        String token = registerAndGetToken("Siti", "siti@finpulse.app");

        // INCOME category rejected -> 400
        CreateBudgetRequest incomeBudget = new CreateBudgetRequest(
                incomeCategory.getId(), 11, 2026, new BigDecimal("5000000")
        );
        mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incomeBudget)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Duplicate (user, category, month, year) -> 409 Conflict
        CreateBudgetRequest foodBudget = new CreateBudgetRequest(
                foodCategory.getId(), 11, 2026, new BigDecimal("2000000")
        );
        mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(foodBudget)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(foodBudget)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Only month without year -> 400
        mockMvc.perform(get("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Only year without month -> 400
        mockMvc.perform(get("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Neither month nor year -> defaults to current period (200 OK)
        mockMvc.perform(get("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Out-of-range period query params -> 400
        mockMvc.perform(get("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "0")
                        .param("year", "2026"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "13")
                        .param("year", "2026"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "11")
                        .param("year", "2019"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "11")
                        .param("year", "2101"))
                .andExpect(status().isBadRequest());

        // Invalid amountLimit: zero, negative, scale > 2 (100.999), > max -> 400
        for (BigDecimal badLimit : new BigDecimal[]{
                BigDecimal.ZERO,
                new BigDecimal("-50000"),
                new BigDecimal("100.999"),
                new BigDecimal("10000000000000.00")
        }) {
            CreateBudgetRequest badLimitReq = new CreateBudgetRequest(
                    transportCategory.getId(), 11, 2026, badLimit
            );
            mockMvc.perform(post("/api/budgets")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badLimitReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors.amountLimit").isNotEmpty());
        }

        // Unauthenticated request -> 401
        mockMvc.perform(get("/api/budgets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void budgetUserIsolationShouldPreventCrossUserAccessAndSpendingLeakage() throws Exception {
        String tokenA = registerAndGetToken("User A", "usera@finpulse.app");
        String tokenB = registerAndGetToken("User B", "userb@finpulse.app");

        createTransaction(tokenA, foodCategory.getId(), TransactionType.EXPENSE, "900000", "2026-11-10");
        createTransaction(tokenB, foodCategory.getId(), TransactionType.EXPENSE, "250000", "2026-11-12");

        CreateBudgetRequest budgetReq = new CreateBudgetRequest(
                foodCategory.getId(), 11, 2026, new BigDecimal("1000000")
        );
        MvcResult budgetAResult = mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(budgetReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spentAmount").value(900000.0))
                .andReturn();
        String budgetAId = objectMapper.readTree(budgetAResult.getResponse().getContentAsString()).get("id").asText();

        // User B can create budget for same category/period independently with only User B's spending
        mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(budgetReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spentAmount").value(250000.0));

        // User B cannot update or delete User A's budget -> 404
        mockMvc.perform(put("/api/budgets/{id}", budgetAId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBudgetRequest(new BigDecimal("500000")))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/budgets/{id}", budgetAId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // User B cannot create a budget using User A's private EXPENSE category -> 404
        User userA = userRepository.findByEmailIgnoreCase("usera@finpulse.app").orElseThrow();
        Category privateCategoryA = categoryRepository.save(
                new Category(userA, "Private Expense A", TransactionType.EXPENSE, "lock", "#222222", false)
        );
        CreateBudgetRequest crossCategoryBudget = new CreateBudgetRequest(
                privateCategoryA.getId(), 11, 2026, new BigDecimal("500000")
        );
        mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossCategoryBudget)))
                .andExpect(status().isNotFound());
    }

    private String registerAndGetToken(String fullName, String email) throws Exception {
        RegisterRequest req = new RegisterRequest(fullName, email, "Rahasia123!");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private void createTransaction(
            String token,
            UUID categoryId,
            TransactionType type,
            String amount,
            String date
    ) throws Exception {
        CreateTransactionRequest req = new CreateTransactionRequest(
                categoryId, type, new BigDecimal(amount), "Tx " + date, LocalDate.parse(date), null
        );
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
