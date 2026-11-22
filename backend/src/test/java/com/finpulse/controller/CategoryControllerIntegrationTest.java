package com.finpulse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpulse.dto.RegisterRequest;
import com.finpulse.entity.Category;
import com.finpulse.entity.TransactionType;
import com.finpulse.entity.User;
import com.finpulse.repository.CategoryRepository;
import com.finpulse.repository.RefreshTokenRepository;
import com.finpulse.repository.UserRepository;
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

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setupDefaults() {
        refreshTokenRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        categoryRepository.save(new Category(null, "Gaji", TransactionType.INCOME, "wallet", "#10B981", true));
        categoryRepository.save(new Category(null, "Investasi", TransactionType.INCOME, "trending-up", "#059669", true));
        categoryRepository.save(new Category(null, "Makanan & Minuman", TransactionType.EXPENSE, "utensils", "#F59E0B", true));
        categoryRepository.save(new Category(null, "Transportasi", TransactionType.EXPENSE, "car", "#3B82F6", true));
    }

    @Test
    void getCategoriesWithoutAuthShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/categories"));
    }

    @Test
    void getCategoriesShouldReturnDefaultsAndIsolateCustomCategoriesPerUser() throws Exception {
        JsonNode authUserA = registerUser("User A", "a@finpulse.app", "Rahasia123!");
        JsonNode authUserB = registerUser("User B", "b@finpulse.app", "Rahasia123!");

        User userA = userRepository.findByEmailIgnoreCase("a@finpulse.app").orElseThrow();
        User userB = userRepository.findByEmailIgnoreCase("b@finpulse.app").orElseThrow();

        categoryRepository.save(new Category(userA, "Freelance A", TransactionType.INCOME, "briefcase", "#10B981", false));
        categoryRepository.save(new Category(userA, "Kopi A", TransactionType.EXPENSE, "coffee", "#F59E0B", false));
        categoryRepository.save(new Category(userB, "Royalti B", TransactionType.INCOME, "award", "#34D399", false));

        String tokenA = authUserA.get("accessToken").asText();
        String tokenB = authUserB.get("accessToken").asText();

        // User A sees 4 defaults (sorted first) + 2 own custom categories, never User B's category
        mockMvc.perform(get("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[0].name").value("Gaji"))
                .andExpect(jsonPath("$[*].name", hasItem("Freelance A")))
                .andExpect(jsonPath("$[*].name", hasItem("Kopi A")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Royalti B"))));

        // User B sees 4 defaults + 1 own custom category, never User A's categories
        mockMvc.perform(get("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].name", hasItem("Royalti B")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Freelance A"))))
                .andExpect(jsonPath("$[*].name", not(hasItem("Kopi A"))));

        // Filter by INCOME for User A
        mockMvc.perform(get("/api/categories")
                        .param("type", "INCOME")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].type", everyItem(is("INCOME"))))
                .andExpect(jsonPath("$[*].name", hasItem("Freelance A")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Royalti B"))));

        // Filter by EXPENSE for User A
        mockMvc.perform(get("/api/categories")
                        .param("type", "EXPENSE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].type", everyItem(is("EXPENSE"))))
                .andExpect(jsonPath("$[*].name", hasItem("Kopi A")));
    }

    @Test
    void getCategoriesWithInvalidTypeParamShouldReturn400() throws Exception {
        JsonNode auth = registerUser("User A", "a@finpulse.app", "Rahasia123!");
        String token = auth.get("accessToken").asText();

        mockMvc.perform(get("/api/categories")
                        .param("type", "INVALID_TYPE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/categories"));
    }

    private JsonNode registerUser(String fullName, String email, String password) throws Exception {
        RegisterRequest request = new RegisterRequest(fullName, email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
