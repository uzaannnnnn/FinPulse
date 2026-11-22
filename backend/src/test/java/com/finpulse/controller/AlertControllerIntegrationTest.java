package com.finpulse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpulse.dto.RegisterRequest;
import com.finpulse.entity.Budget;
import com.finpulse.entity.BudgetAlert;
import com.finpulse.entity.Category;
import com.finpulse.entity.TransactionType;
import com.finpulse.entity.User;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BudgetAlertRepository budgetAlertRepository;

    @Autowired
    private ReportExportRepository reportExportRepository;

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

    private Category foodCategory;
    private Category billsCategory;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        foodCategory = categoryRepository.save(
                new Category(null, "Makanan & Minuman", TransactionType.EXPENSE, "utensils", "#F59E0B", true)
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
        budgetAlertRepository.deleteAllInBatch();
        reportExportRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        budgetRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void alertsLifecycleAndStrictUserIsolationShouldWork() throws Exception {
        String tokenA = registerAndGetToken("Alert User A", "alerta@finpulse.app");
        String tokenB = registerAndGetToken("Alert User B", "alertb@finpulse.app");

        User userA = userRepository.findByEmailIgnoreCase("alerta@finpulse.app").orElseThrow();
        User userB = userRepository.findByEmailIgnoreCase("alertb@finpulse.app").orElseThrow();

        Budget budgetA = budgetRepository.save(new Budget(userA, foodCategory, 11, 2026, new BigDecimal("2000000.00")));

        BudgetAlert alertA1 = budgetAlertRepository.save(new BudgetAlert(
                userA, budgetA, foodCategory, 11, 2026,
                new BigDecimal("2000000.00"), new BigDecimal("2300000.00"), new BigDecimal("115.00"),
                "Pengeluaran kategori Makanan & Minuman pada Nov 2026 mencapai 115.00% dari batas anggaran."
        ));
        BudgetAlert alertA2 = budgetAlertRepository.save(new BudgetAlert(
                userA, null, billsCategory, 11, 2026,
                new BigDecimal("1000000.00"), new BigDecimal("1250000.00"), new BigDecimal("125.00"),
                "Pengeluaran kategori Tagihan & Utilitas pada Nov 2026 mencapai 125.00% dari batas anggaran."
        ));

        budgetAlertRepository.save(new BudgetAlert(
                userB, null, foodCategory, 11, 2026,
                new BigDecimal("500000.00"), new BigDecimal("700000.00"), new BigDecimal("140.00"),
                "Alert milik User B"
        ));

        // 1. User A lists all alerts -> sees 2 unread alerts, never User B's
        mockMvc.perform(get("/api/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.alerts", hasSize(2)))
                .andExpect(jsonPath("$.alerts[0].isRead").value(false));

        // 2. Mark alertA1 as read -> 200 with isRead=true
        mockMvc.perform(patch("/api/alerts/{id}/read", alertA1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertA1.getId().toString()))
                .andExpect(jsonPath("$.isRead").value(true));

        // 3. List with unreadOnly=true -> sees only 1 unread alert
        mockMvc.perform(get("/api/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].id").value(alertA2.getId().toString()));

        // 4. User B attempts to mark or delete User A's alert -> 404
        mockMvc.perform(patch("/api/alerts/{id}/read", alertA2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(delete("/api/alerts/{id}", alertA2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 5. Mark all as read -> 204, unreadCount becomes 0 for User A (User B still has 1 unread)
        mockMvc.perform(patch("/api/alerts/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.alerts", hasSize(2)));

        mockMvc.perform(get("/api/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.alerts", hasSize(1)));

        // 6. Delete alertA1 -> 204
        mockMvc.perform(delete("/api/alerts/{id}", alertA1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)));

        // 7. Validation & auth checks
        mockMvc.perform(get("/api/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/alerts/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isUnauthorized());
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
}
