package com.finpulse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpulse.dto.CreateTransactionRequest;
import com.finpulse.dto.ReceiptUploadResponse;
import com.finpulse.dto.RegisterRequest;
import com.finpulse.dto.UpdateTransactionRequest;
import com.finpulse.entity.Category;
import com.finpulse.entity.TransactionType;
import com.finpulse.entity.User;
import com.finpulse.repository.BudgetRepository;
import com.finpulse.repository.CategoryRepository;
import com.finpulse.repository.RefreshTokenRepository;
import com.finpulse.repository.TransactionRepository;
import com.finpulse.repository.UserRepository;
import com.finpulse.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerIntegrationTest {

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

    @MockitoSpyBean
    private StorageService storageService;

    private Category incomeCategory;
    private Category foodCategory;
    private Category transportCategory;

    @BeforeEach
    void setUp() {
        reset(storageService);
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
        reset(storageService);
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
    void endpointsWithoutAuthenticationShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/transactions"));
    }

    @Test
    void crudTransactionLifecycleShouldWorkEndToEnd() throws Exception {
        String token = registerAndGetToken("Budi", "budi@finpulse.app");

        CreateTransactionRequest createReq = new CreateTransactionRequest(
                foodCategory.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("125000.00"),
                "Makan siang tim",
                LocalDate.of(2026, 11, 21),
                "   "
        );

        MvcResult createResult = mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.categoryId").value(foodCategory.getId().toString()))
                .andExpect(jsonPath("$.categoryName").value("Makanan & Minuman"))
                .andExpect(jsonPath("$.categoryIcon").value("utensils"))
                .andExpect(jsonPath("$.categoryColor").value("#F59E0B"))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.amount").value(125000.0))
                .andExpect(jsonPath("$.description").value("Makan siang tim"))
                .andExpect(jsonPath("$.transactionDate").value("2026-11-21"))
                .andExpect(jsonPath("$.receiptUrl").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        String txId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/transactions/{id}", txId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(txId))
                .andExpect(jsonPath("$.amount").value(125000.0));

        UpdateTransactionRequest updateReq = new UpdateTransactionRequest(
                transportCategory.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("150000.50"),
                "Taksi bandara",
                LocalDate.of(2026, 11, 22),
                "https://demo.supabase.co/storage/v1/object/public/receipts/user/tx.jpg"
        );

        mockMvc.perform(put("/api/transactions/{id}", txId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(transportCategory.getId().toString()))
                .andExpect(jsonPath("$.categoryName").value("Transportasi"))
                .andExpect(jsonPath("$.amount").value(150000.5))
                .andExpect(jsonPath("$.description").value("Taksi bandara"))
                .andExpect(jsonPath("$.transactionDate").value("2026-11-22"))
                .andExpect(jsonPath("$.receiptUrl")
                        .value("https://demo.supabase.co/storage/v1/object/public/receipts/user/tx.jpg"));

        mockMvc.perform(delete("/api/transactions/{id}", txId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions/{id}", txId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listTransactionsShouldSupportFiltersSortingAndPagination() throws Exception {
        String token = registerAndGetToken("Ani", "ani@finpulse.app");

        createTransaction(token, incomeCategory.getId(), TransactionType.INCOME, "10000000", "Gaji November", "2026-11-01");
        createTransaction(token, foodCategory.getId(), TransactionType.EXPENSE, "50000", "Kopi Pagi", "2026-11-05");
        createTransaction(token, foodCategory.getId(), TransactionType.EXPENSE, "200000", "Makan Malam Diskon 50%", "2026-11-10");
        createTransaction(token, transportCategory.getId(), TransactionType.EXPENSE, "75000", "Ojek Online", "2026-11-15");
        createTransaction(token, foodCategory.getId(), TransactionType.EXPENSE, "90000", "Kopi Sore", "2026-10-20");

        // Pagination: page 0 size 2
        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].transactionDate").value("2026-11-15"));

        // Filter by type, categoryId, date range, and sort by amount ASC
        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("type", "EXPENSE")
                        .param("categoryId", foodCategory.getId().toString())
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-30")
                        .param("sortBy", "amount")
                        .param("sortDir", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].amount").value(50000.0))
                .andExpect(jsonPath("$.content[1].amount").value(200000.0));

        // Case-insensitive search and literal '%' matching
        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "kopi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "50%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].description").value("Makan Malam Diskon 50%"));
    }

    @Test
    void validationRulesShouldRejectInvalidInputsAndMaliciousReceiptUrl() throws Exception {
        String token = registerAndGetToken("Cici", "cici@finpulse.app");

        // Type mismatch: EXPENSE transaction with INCOME category -> 400
        CreateTransactionRequest typeMismatch = new CreateTransactionRequest(
                incomeCategory.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("50000"),
                "Salah tipe",
                LocalDate.of(2026, 11, 21),
                null
        );
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(typeMismatch)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // XSS payload in receiptUrl -> 400
        CreateTransactionRequest xssUrl = new CreateTransactionRequest(
                foodCategory.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("50000"),
                "XSS test",
                LocalDate.of(2026, 11, 21),
                "javascript:alert(1)"
        );
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(xssUrl)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Negative amount -> 400 with validationErrors
        CreateTransactionRequest negativeAmount = new CreateTransactionRequest(
                foodCategory.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("-1000"),
                "Negatif",
                LocalDate.of(2026, 11, 21),
                null
        );
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeAmount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.amount").isNotEmpty());

        // startDate > endDate -> 400
        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("startDate", "2026-11-30")
                        .param("endDate", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Invalid sortBy -> 400
        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("sortBy", "passwordHash"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Invalid sortDir, page < 0, size <= 0, size > 100, search > 100 chars -> 400
        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("sortDir", "RANDOM"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("size", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "a".repeat(101)))
                .andExpect(status().isBadRequest());

        // Zero amount, decimal scale > 2 (100.999), amount > max -> 400
        CreateTransactionRequest zeroAmount = new CreateTransactionRequest(
                foodCategory.getId(), TransactionType.EXPENSE, BigDecimal.ZERO, "Zero", LocalDate.of(2026, 11, 21), null
        );
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroAmount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.amount").isNotEmpty());

        CreateTransactionRequest threeDecimals = new CreateTransactionRequest(
                foodCategory.getId(), TransactionType.EXPENSE, new BigDecimal("100.999"), "3 dec", LocalDate.of(2026, 11, 21), null
        );
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(threeDecimals)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.amount").isNotEmpty());

        CreateTransactionRequest overMaxAmount = new CreateTransactionRequest(
                foodCategory.getId(), TransactionType.EXPENSE, new BigDecimal("10000000000000.00"), "Over max", LocalDate.of(2026, 11, 21), null
        );
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overMaxAmount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.amount").isNotEmpty());

        // Description > 500 chars -> 400
        CreateTransactionRequest longDescription = new CreateTransactionRequest(
                foodCategory.getId(), TransactionType.EXPENSE, new BigDecimal("50000"), "x".repeat(501), LocalDate.of(2026, 11, 21), null
        );
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longDescription)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.description").isNotEmpty());

        // data:, ftp:, credential-embedding receiptUrl -> 400
        for (String badUrl : new String[]{
                "data:image/svg+xml,<svg onload=alert(1)>",
                "ftp://files.example.com/receipt.jpg",
                "https://user:pass@evil.example.com/receipt.jpg"
        }) {
            CreateTransactionRequest badUrlReq = new CreateTransactionRequest(
                    foodCategory.getId(), TransactionType.EXPENSE, new BigDecimal("50000"), "Bad URL", LocalDate.of(2026, 11, 21), badUrl
            );
            mockMvc.perform(post("/api/transactions")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badUrlReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        // SQL/LIKE injection characters in search must be safely parameterized and escaped
        createTransaction(token, foodCategory.getId(), TransactionType.EXPENSE, "25000", "Snack biasa", "2026-11-21");
        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // Invalid UUID path parameter -> 400
        mockMvc.perform(get("/api/transactions/{id}", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void userIsolationShouldReturn404WhenAccessingAnotherUsersTransactionOrCategory() throws Exception {
        String tokenA = registerAndGetToken("User A", "usera@finpulse.app");
        String tokenB = registerAndGetToken("User B", "userb@finpulse.app");

        User userA = userRepository.findByEmailIgnoreCase("usera@finpulse.app").orElseThrow();
        Category customCategoryA = categoryRepository.save(
                new Category(userA, "Private A", TransactionType.EXPENSE, "lock", "#111111", false)
        );

        String txIdA = createTransaction(
                tokenA, foodCategory.getId(), TransactionType.EXPENSE, "80000", "Transaksi User A", "2026-11-21"
        );

        // User B list does not include User A's transaction
        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));

        // User B GET/PUT/DELETE on User A's transaction -> 404
        mockMvc.perform(get("/api/transactions/{id}", txIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        UpdateTransactionRequest updateReq = new UpdateTransactionRequest(
                foodCategory.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("10000"),
                "Hack",
                LocalDate.of(2026, 11, 21),
                null
        );
        mockMvc.perform(put("/api/transactions/{id}", txIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/transactions/{id}", txIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // User B cannot create transaction using User A's private category -> 404
        CreateTransactionRequest usingOtherCategory = new CreateTransactionRequest(
                customCategoryA.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("15000"),
                "Unauthorized category",
                LocalDate.of(2026, 11, 21),
                null
        );
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usingOtherCategory)))
                .andExpect(status().isNotFound());

        // User B cannot update own transaction to use User A's private category -> 404
        String txIdB = createTransaction(
                tokenB, foodCategory.getId(), TransactionType.EXPENSE, "30000", "Transaksi User B", "2026-11-21"
        );
        UpdateTransactionRequest updateToOtherCategory = new UpdateTransactionRequest(
                customCategoryA.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("30000"),
                "Switch category hack",
                LocalDate.of(2026, 11, 21),
                null
        );
        mockMvc.perform(put("/api/transactions/{id}", txIdB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateToOtherCategory)))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadReceiptShouldHandleMockedSuccessInvalidMagicBytes400AndUnconfiguredStorage503() throws Exception {
        String token = registerAndGetToken("Dina", "dina@finpulse.app");
        byte[] validJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

        // 1. Empty file and missing multipart 'file' part -> 400
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        mockMvc.perform(multipart("/api/transactions/receipt")
                        .file(emptyFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(multipart("/api/transactions/receipt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 2. Invalid magic bytes (real SupabaseStorageService validation) -> 400
        MockMultipartFile fakeJpeg = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "plain text payload".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/transactions/receipt")
                        .file(fakeJpeg)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/transactions/receipt"));

        // 2. Valid JPEG when Supabase Storage env vars are not configured -> 503
        MockMultipartFile validFile = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", validJpeg);
        mockMvc.perform(multipart("/api/transactions/receipt")
                        .file(validFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.path").value("/api/transactions/receipt"));

        // 3. Mocked StorageService success -> 201
        ReceiptUploadResponse mockedResponse = new ReceiptUploadResponse(
                "https://demo.supabase.co/storage/v1/object/public/receipts/u/abc.jpg",
                "u/abc.jpg",
                "image/jpeg",
                validJpeg.length
        );
        doReturn(mockedResponse).when(storageService).uploadReceipt(any(UUID.class), any());

        mockMvc.perform(multipart("/api/transactions/receipt")
                        .file(validFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptUrl").value(mockedResponse.receiptUrl()))
                .andExpect(jsonPath("$.filePath").value("u/abc.jpg"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.sizeBytes").value(validJpeg.length));
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

    private String createTransaction(
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
        MvcResult result = mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
