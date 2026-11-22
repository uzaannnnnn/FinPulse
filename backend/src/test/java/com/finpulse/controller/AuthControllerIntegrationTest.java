package com.finpulse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpulse.config.JwtProperties;
import com.finpulse.dto.LoginRequest;
import com.finpulse.dto.RefreshTokenRequest;
import com.finpulse.dto.RegisterRequest;
import com.finpulse.entity.RefreshToken;
import com.finpulse.entity.User;
import com.finpulse.repository.CategoryRepository;
import com.finpulse.repository.RefreshTokenRepository;
import com.finpulse.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void registerShouldCreateUserHashPasswordAndReturnTokens() throws Exception {
        RegisterRequest request = new RegisterRequest("Budi Santoso", "Budi@FinPulse.app", "Rahasia123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("budi@finpulse.app"))
                .andExpect(jsonPath("$.user.fullName").value("Budi Santoso"))
                .andExpect(jsonPath("$.user.createdAt").isNotEmpty());

        User savedUser = userRepository.findByEmailIgnoreCase("budi@finpulse.app").orElseThrow();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("Rahasia123!");
        assertThat(savedUser.getPasswordHash()).startsWith("$2");
    }

    @Test
    void registerWithDuplicateEmailShouldReturn409Conflict() throws Exception {
        registerUser("Budi Santoso", "budi@finpulse.app", "Rahasia123!");

        RegisterRequest duplicate = new RegisterRequest("Budi Lain", "BUDI@finpulse.app", "Rahasia123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void registerWithInvalidInputShouldReturn400WithValidationErrors() throws Exception {
        RegisterRequest invalid = new RegisterRequest("A", "not-an-email", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.validationErrors.fullName").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.password").isNotEmpty());
    }

    @Test
    void registerWithMalformedJsonShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"broken\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void loginShouldSucceedWithValidCredentialsAndFailWithInvalidCredentials() throws Exception {
        registerUser("Budi Santoso", "budi@finpulse.app", "Rahasia123!");

        LoginRequest validLogin = new LoginRequest("budi@finpulse.app", "Rahasia123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("budi@finpulse.app"));

        LoginRequest wrongPassword = new LoginRequest("budi@finpulse.app", "SalahBanget!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));

        LoginRequest unknownUser = new LoginRequest("tidakada@finpulse.app", "Rahasia123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownUser)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refreshShouldRotateTokenAndRejectReusedOrExpiredTokens() throws Exception {
        JsonNode auth = registerUser("Budi Santoso", "budi@finpulse.app", "Rahasia123!");
        String oldRefreshToken = auth.get("refreshToken").asText();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(oldRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        JsonNode rotatedAuth = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newRefreshToken = rotatedAuth.get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

        // Old refresh token must be revoked and rejected
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(oldRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // Expired token in DB must also be rejected
        User user = userRepository.findByEmailIgnoreCase("budi@finpulse.app").orElseThrow();
        RefreshToken expired = new RefreshToken(user, "expired-refresh-token-value", Instant.now().minusSeconds(60));
        refreshTokenRepository.save(expired);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("expired-refresh-token-value"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // Blank token returns 400
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.refreshToken").isNotEmpty());
    }

    @Test
    void logoutShouldRevokeOnlyCurrentUsersRefreshTokensAndReturn204() throws Exception {
        JsonNode authUserA = registerUser("User A", "usera@finpulse.app", "Rahasia123!");
        JsonNode authUserB = registerUser("User B", "userb@finpulse.app", "Rahasia123!");

        String accessA = authUserA.get("accessToken").asText();
        String refreshA = authUserA.get("refreshToken").asText();
        String refreshB = authUserB.get("refreshToken").asText();

        // User A attempts logout even passing User B's refreshToken; only User A's tokens must be revoked
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshB))))
                .andExpect(status().isNoContent());

        // User A's refresh token is now revoked
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshA))))
                .andExpect(status().isUnauthorized());

        // User B's refresh token remains valid
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("userb@finpulse.app"));
    }

    @Test
    void logoutWithoutRequestBodyShouldRevokeCurrentUserRefreshTokens() throws Exception {
        JsonNode auth = registerUser("User NoBody", "nobody@finpulse.app", "Rahasia123!");
        String accessToken = auth.get("accessToken").asText();
        String refreshToken = auth.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldReturnCurrentUserWhenAuthenticatedAnd401WhenUnauthenticatedOrExpired() throws Exception {
        JsonNode auth = registerUser("Budi Santoso", "budi@finpulse.app", "Rahasia123!");
        String accessToken = auth.get("accessToken").asText();
        String userId = auth.get("user").get("id").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value("budi@finpulse.app"))
                .andExpect(jsonPath("$.fullName").value("Budi Santoso"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // Missing token -> 401 JSON ErrorResponse
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        // Expired token -> 401 JSON ErrorResponse
        String expiredJwt = Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    @Test
    void actuatorHealthOpenApiAndCorsPreflightShouldBeAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FinPulse API"));

        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void registerWithExceedingMaxLengthsAndUtf8BytesShouldReturn400() throws Exception {
        RegisterRequest exceedingLengths = new RegisterRequest(
                "A".repeat(101),
                "a".repeat(245) + "@finpulse.app",
                "p".repeat(73)
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exceedingLengths)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.fullName").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.password").isNotEmpty());

        // 40 accented chars = 40 Java chars (<= 72), but 80 UTF-8 bytes (> 72 BCrypt limit)
        RegisterRequest multiBytePassword = new RegisterRequest(
                " A ",
                "budi@finpulse.app",
                "é".repeat(40)
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(multiBytePassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.fullName").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.password").isNotEmpty());
    }

    @Test
    void loginShouldRejectPasswordExceeding72BytesEvenIfPrefixMatches() throws Exception {
        String maxAsciiPassword = "a".repeat(72);
        registerUser("Budi Santoso", "budi@finpulse.app", maxAsciiPassword);

        LoginRequest extendedPasswordLogin = new LoginRequest("budi@finpulse.app", maxAsciiPassword + "extra");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(extendedPasswordLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void meShouldRejectTamperedJwtAndReturnIsoTimestampInErrorResponse() throws Exception {
        JsonNode auth = registerUser("Budi Santoso", "budi@finpulse.app", "Rahasia123!");
        String validToken = auth.get("accessToken").asText();
        String tamperedToken = validToken.substring(0, validToken.length() - 2) + "zz";

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.timestamp", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T.*Z$")));

        // Signed token without sub claim must return 401, never 500
        String tokenWithoutSubject = Jwts.builder()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithoutSubject))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void unsupportedHttpMethodAndMediaTypeShouldReturnStandardErrorResponseWithoutStackTrace() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Exception"))));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
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
