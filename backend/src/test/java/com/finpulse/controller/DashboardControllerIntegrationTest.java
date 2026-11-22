package com.finpulse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpulse.config.CacheConfig;
import com.finpulse.dto.CreateBudgetRequest;
import com.finpulse.dto.CreateTransactionRequest;
import com.finpulse.dto.RegisterRequest;
import com.finpulse.entity.Category;
import com.finpulse.entity.TransactionType;
import com.finpulse.repository.BudgetAlertRepository;
import com.finpulse.repository.BudgetRepository;
import com.finpulse.repository.CategoryRepository;
import com.finpulse.repository.RefreshTokenRepository;
import com.finpulse.repository.ReportExportRepository;
import com.finpulse.repository.TransactionRepository;
import com.finpulse.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIntegrationTest {

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

    @Autowired
    private BudgetAlertRepository budgetAlertRepository;

    @Autowired
    private ReportExportRepository reportExportRepository;

    @Autowired
    private CacheManager cacheManager;

    private Category salaryCategory;
    private Category foodCategory;
    private Category transportCategory;
    private Category billsCategory;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        salaryCategory = categoryRepository.save(
                new Category(null, "Gaji", TransactionType.INCOME, "wallet", "#10B981", true)
        );
        foodCategory = categoryRepository.save(
                new Category(null, "Makanan & Minuman", TransactionType.EXPENSE, "utensils", "#F59E0B", true)
        );
        transportCategory = categoryRepository.save(
                new Category(null, "Transportasi", TransactionType.EXPENSE, "car", "#3B82F6", true)
        );
        billsCategory = categoryRepository.save(
                new Category(null, "Tagihan & Utilitas", TransactionType.EXPENSE, "receipt", "#EF4444", true)
        );
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        Cache cache = cacheManager.getCache(CacheConfig.DASHBOARD_SUMMARY_CACHE);
        if (cache != null) {
            cache.clear();
        }
        budgetAlertRepository.deleteAllInBatch();
        reportExportRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        budgetRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void summaryShouldComputeKpisCashflowSeriesBreakdownBudgetsRecentTransactionsAndIsolateUsers() throws Exception {
        String tokenA = registerAndGetToken("User A", "usera@finpulse.app");
        String tokenB = registerAndGetToken("User B", "userb@finpulse.app");

        // Earlier historical transaction before 6-month window (Jan 2026): +5,000,000
        createTransaction(tokenA, salaryCategory.getId(), TransactionType.INCOME, "5000000", "Bonus Awal Tahun", "2026-01-15");

        // Within 6-month window (Jun 2026 - Nov 2026):
        // Sep 2026: +8,000,000 income, -2,000,000 expense -> net +6,000,000
        createTransaction(tokenA, salaryCategory.getId(), TransactionType.INCOME, "8000000", "Gaji Sep", "2026-09-01");
        createTransaction(tokenA, foodCategory.getId(), TransactionType.EXPENSE, "2000000", "Makan Sep", "2026-09-10");

        // Nov 2026 (6 transactions total to verify recentTransactions caps at 5):
        // Income: 10,000,000
        // Expense: Food 3,000,000 (60.0%), Bills 1,500,000 (30.0%), Transport 500,000 (10.0%) -> Total Expense 5,000,000
        createTransaction(tokenA, salaryCategory.getId(), TransactionType.INCOME, "10000000", "Gaji Nov", "2026-11-01");
        createTransaction(tokenA, foodCategory.getId(), TransactionType.EXPENSE, "1000000", "Belanja Mingguan 1", "2026-11-05");
        createTransaction(tokenA, billsCategory.getId(), TransactionType.EXPENSE, "1500000", "Listrik & Internet", "2026-11-10");
        createTransaction(tokenA, foodCategory.getId(), TransactionType.EXPENSE, "2000000", "Makan Keluarga", "2026-11-15");
        createTransaction(tokenA, transportCategory.getId(), TransactionType.EXPENSE, "200000", "Bensin", "2026-11-18");
        createTransaction(tokenA, transportCategory.getId(), TransactionType.EXPENSE, "300000", "Tol & Parkir", "2026-11-21");

        // Budgets for Nov 2026:
        // Food: spent 3,000,000 / limit 2,500,000 -> 120.0% (over)
        // Bills: spent 1,500,000 / limit 2,000,000 -> 75.0%
        // Transport: spent 500,000 / limit 2,000,000 -> 25.0%
        createBudget(tokenA, transportCategory.getId(), 11, 2026, "2000000");
        createBudget(tokenA, foodCategory.getId(), 11, 2026, "2500000");
        createBudget(tokenA, billsCategory.getId(), 11, 2026, "2000000");

        // User B noise data in the exact same month (must NOT affect User A's summary!)
        createTransaction(tokenB, salaryCategory.getId(), TransactionType.INCOME, "99000000", "Gaji B", "2026-11-01");
        createTransaction(tokenB, foodCategory.getId(), TransactionType.EXPENSE, "50000000", "Makan B", "2026-11-20");
        createBudget(tokenB, foodCategory.getId(), 11, 2026, "1000000");

        // Cumulative balance for User A:
        // All income = 5,000,000 + 8,000,000 + 10,000,000 = 23,000,000
        // All expense = 2,000,000 + 5,000,000 = 7,000,000
        // Cumulative = 16,000,000
        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("month", "11")
                        .param("year", "2026")
                        .param("monthsBack", "6"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Status", "MISS"))
                .andExpect(jsonPath("$.generatedAt", notNullValue()))
                .andExpect(jsonPath("$.fromCache").value(false))
                .andExpect(jsonPath("$.month").value(11))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.totalIncome").value(10000000.0))
                .andExpect(jsonPath("$.totalExpense").value(5000000.0))
                .andExpect(jsonPath("$.balance").value(5000000.0))
                .andExpect(jsonPath("$.cumulativeBalance").value(16000000.0))
                .andExpect(jsonPath("$.transactionCount").value(6))

                // 6 chronological months: Jun, Jul, Aug, Sep, Oct, Nov 2026
                .andExpect(jsonPath("$.cashflowSeries", hasSize(6)))
                .andExpect(jsonPath("$.cashflowSeries[0].label").value("Jun 2026"))
                .andExpect(jsonPath("$.cashflowSeries[0].totalIncome").value(0.0))
                .andExpect(jsonPath("$.cashflowSeries[0].totalExpense").value(0.0))
                .andExpect(jsonPath("$.cashflowSeries[0].net").value(0.0))
                .andExpect(jsonPath("$.cashflowSeries[3].label").value("Sep 2026"))
                .andExpect(jsonPath("$.cashflowSeries[3].totalIncome").value(8000000.0))
                .andExpect(jsonPath("$.cashflowSeries[3].totalExpense").value(2000000.0))
                .andExpect(jsonPath("$.cashflowSeries[3].net").value(6000000.0))
                .andExpect(jsonPath("$.cashflowSeries[4].label").value("Oct 2026"))
                .andExpect(jsonPath("$.cashflowSeries[4].totalIncome").value(0.0))
                .andExpect(jsonPath("$.cashflowSeries[5].label").value("Nov 2026"))
                .andExpect(jsonPath("$.cashflowSeries[5].totalIncome").value(10000000.0))
                .andExpect(jsonPath("$.cashflowSeries[5].totalExpense").value(5000000.0))
                .andExpect(jsonPath("$.cashflowSeries[5].net").value(5000000.0))

                // Expense breakdown ordered by amount DESC
                .andExpect(jsonPath("$.expenseByCategory", hasSize(3)))
                .andExpect(jsonPath("$.expenseByCategory[0].categoryName").value("Makanan & Minuman"))
                .andExpect(jsonPath("$.expenseByCategory[0].amount").value(3000000.0))
                .andExpect(jsonPath("$.expenseByCategory[0].percentage").value(60.0))
                .andExpect(jsonPath("$.expenseByCategory[1].categoryName").value("Tagihan & Utilitas"))
                .andExpect(jsonPath("$.expenseByCategory[1].amount").value(1500000.0))
                .andExpect(jsonPath("$.expenseByCategory[1].percentage").value(30.0))
                .andExpect(jsonPath("$.expenseByCategory[2].categoryName").value("Transportasi"))
                .andExpect(jsonPath("$.expenseByCategory[2].amount").value(500000.0))
                .andExpect(jsonPath("$.expenseByCategory[2].percentage").value(10.0))

                // Budget progress ordered by utilizationPercentage DESC
                .andExpect(jsonPath("$.budgetProgress", hasSize(3)))
                .andExpect(jsonPath("$.budgetProgress[0].categoryName").value("Makanan & Minuman"))
                .andExpect(jsonPath("$.budgetProgress[0].utilizationPercentage").value(120.0))
                .andExpect(jsonPath("$.budgetProgress[0].isOverBudget").value(true))
                .andExpect(jsonPath("$.budgetProgress[1].categoryName").value("Tagihan & Utilitas"))
                .andExpect(jsonPath("$.budgetProgress[1].utilizationPercentage").value(75.0))
                .andExpect(jsonPath("$.budgetProgress[1].isOverBudget").value(false))
                .andExpect(jsonPath("$.budgetProgress[2].categoryName").value("Transportasi"))
                .andExpect(jsonPath("$.budgetProgress[2].utilizationPercentage").value(25.0))

                // Recent transactions capped at 5, ordered transactionDate DESC
                .andExpect(jsonPath("$.recentTransactions", hasSize(5)))
                .andExpect(jsonPath("$.recentTransactions[0].description").value("Tol & Parkir"))
                .andExpect(jsonPath("$.recentTransactions[1].description").value("Bensin"))
                .andExpect(jsonPath("$.recentTransactions[4].description").value("Belanja Mingguan 1"));
    }

    @Test
    void summaryForEmptyUserAndInvalidParametersShouldBehaveCorrectly() throws Exception {
        String token = registerAndGetToken("New User", "empty@finpulse.app");

        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "11")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(0.0))
                .andExpect(jsonPath("$.totalExpense").value(0.0))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.cumulativeBalance").value(0.0))
                .andExpect(jsonPath("$.transactionCount").value(0))
                .andExpect(jsonPath("$.cashflowSeries", hasSize(6)))
                .andExpect(jsonPath("$.expenseByCategory", hasSize(0)))
                .andExpect(jsonPath("$.budgetProgress", hasSize(0)))
                .andExpect(jsonPath("$.recentTransactions", hasSize(0)));

        // monthsBack out of range -> 400
        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("monthsBack", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Only month without year -> 400
        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Only year without month, monthsBack <= 0, out-of-range month/year -> 400
        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("year", "2026"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("monthsBack", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "13")
                        .param("year", "2026"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "2")
                        .param("year", "2019"))
                .andExpect(status().isBadRequest());

        // Cross-year cashflow series (Feb 2026 with monthsBack=6 -> Sep 2025 .. Feb 2026)
        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("month", "2")
                        .param("year", "2026")
                        .param("monthsBack", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashflowSeries", hasSize(6)))
                .andExpect(jsonPath("$.cashflowSeries[0].label").value("Sep 2025"))
                .andExpect(jsonPath("$.cashflowSeries[5].label").value("Feb 2026"));

        // Unauthenticated -> 401
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void summaryShouldCacheHitsEvictAfterMutationsAndIsolateUserCaches() throws Exception {
        String tokenA = registerAndGetToken("Cache User A", "cachea@finpulse.app");
        String tokenB = registerAndGetToken("Cache User B", "cacheb@finpulse.app");

        createTransaction(tokenA, salaryCategory.getId(), TransactionType.INCOME, "5000000", "Gaji A", "2026-11-01");
        createTransaction(tokenB, salaryCategory.getId(), TransactionType.INCOME, "7000000", "Gaji B", "2026-11-01");

        // 1. User A first call -> MISS
        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("month", "11")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Status", "MISS"))
                .andExpect(jsonPath("$.fromCache").value(false))
                .andExpect(jsonPath("$.generatedAt", notNullValue()))
                .andExpect(jsonPath("$.totalIncome").value(5000000.0));

        // 2. User A second call -> HIT
        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("month", "11")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Status", "HIT"))
                .andExpect(jsonPath("$.fromCache").value(true))
                .andExpect(jsonPath("$.totalIncome").value(5000000.0));

        // 3. Prime User B cache -> MISS then HIT
        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .param("month", "11")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Status", "MISS"))
                .andExpect(jsonPath("$.fromCache").value(false));

        // 4. User A creates a transaction -> evicts ONLY User A cache
        createTransaction(tokenA, foodCategory.getId(), TransactionType.EXPENSE, "250000", "Makan A", "2026-11-05");

        // User B must still be a cache HIT!
        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .param("month", "11")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Status", "HIT"))
                .andExpect(jsonPath("$.fromCache").value(true))
                .andExpect(jsonPath("$.totalIncome").value(7000000.0));

        // User A must be a cache MISS with updated totals
        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("month", "11")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Status", "MISS"))
                .andExpect(jsonPath("$.fromCache").value(false))
                .andExpect(jsonPath("$.totalExpense").value(250000.0));

        // 5. User A creates a budget -> evicts User A cache again
        createBudget(tokenA, foodCategory.getId(), 11, 2026, "1000000");

        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("month", "11")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Status", "MISS"))
                .andExpect(jsonPath("$.fromCache").value(false))
                .andExpect(jsonPath("$.budgetProgress", hasSize(1)));
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
            String description,
            String date
    ) throws Exception {
        CreateTransactionRequest req = new CreateTransactionRequest(
                categoryId, type, new BigDecimal(amount), description, LocalDate.parse(date), null
        );
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private void createBudget(
            String token,
            UUID categoryId,
            int month,
            int year,
            String amountLimit
    ) throws Exception {
        CreateBudgetRequest req = new CreateBudgetRequest(
                categoryId, month, year, new BigDecimal(amountLimit)
        );
        mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
