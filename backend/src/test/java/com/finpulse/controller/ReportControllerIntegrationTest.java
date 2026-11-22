package com.finpulse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpulse.dto.CreateReportExportRequest;
import com.finpulse.dto.RegisterRequest;
import com.finpulse.entity.ReportExport;
import com.finpulse.entity.ReportFormat;
import com.finpulse.entity.ReportStatus;
import com.finpulse.messaging.RabbitMqConfig;
import com.finpulse.messaging.ReportExportRequestedEvent;
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
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.ConnectException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportExportRepository reportExportRepository;

    @Autowired
    private BudgetAlertRepository budgetAlertRepository;

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

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        reset(rabbitTemplate);
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
    void reportExportSubmissionPollingValidationAndUserIsolationShouldWork() throws Exception {
        String tokenA = registerAndGetToken("Report User A", "reporta@finpulse.app");
        String tokenB = registerAndGetToken("Report User B", "reportb@finpulse.app");

        doNothing().when(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EVENTS_EXCHANGE),
                eq(RabbitMqConfig.REPORT_EXPORT_ROUTING_KEY),
                any(ReportExportRequestedEvent.class)
        );

        // 1. User A submits PDF report export -> 202 Accepted (PENDING)
        CreateReportExportRequest req = new CreateReportExportRequest(11, 2026, ReportFormat.PDF);
        MvcResult createResult = mockMvc.perform(post("/api/reports/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.month").value(11))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.format").value("PDF"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String reportId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EVENTS_EXCHANGE),
                eq(RabbitMqConfig.REPORT_EXPORT_ROUTING_KEY),
                any(ReportExportRequestedEvent.class)
        );

        // 2. User A polls GET /api/reports/{id} and lists GET /api/reports
        mockMvc.perform(get("/api/reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(reportId));

        // 3. User B cannot access User A's report -> 404 & empty list
        mockMvc.perform(get("/api/reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 4. Validation errors -> 400
        mockMvc.perform(post("/api/reports/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReportExportRequest(13, 2026, ReportFormat.CSV))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/reports/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReportExportRequest(11, 2019, ReportFormat.CSV))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());

        // 5. Unknown report id -> 404, unauthenticated -> 401
        mockMvc.perform(get("/api/reports/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportExportShouldReturn503AndMarkJobFailedWhenRabbitMqIsUnavailable() throws Exception {
        String token = registerAndGetToken("Broker Down User", "brokerdown@finpulse.app");

        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(
                        eq(RabbitMqConfig.EVENTS_EXCHANGE),
                        eq(RabbitMqConfig.REPORT_EXPORT_ROUTING_KEY),
                        any(ReportExportRequestedEvent.class)
                );

        CreateReportExportRequest req = new CreateReportExportRequest(11, 2026, ReportFormat.CSV);
        mockMvc.perform(post("/api/reports/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        List<ReportExport> exports = reportExportRepository.findAll();
        assertThat(exports).hasSize(1);
        assertThat(exports.get(0).getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(exports.get(0).getErrorMessage()).isNotBlank();
        assertThat(exports.get(0).getCompletedAt()).isNotNull();
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
