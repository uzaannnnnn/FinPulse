package com.finpulse.security;

import com.finpulse.config.JwtProperties;
import com.finpulse.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String VALID_SECRET = "finpulse-unit-test-secret-key-at-least-32-bytes";

    @Test
    void generateAndValidateAccessTokenShouldContainExpectedClaims() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(VALID_SECRET);
        properties.setAccessExpirationMs(900_000L);
        properties.setRefreshExpirationMs(604_800_000L);

        JwtService jwtService = new JwtService(properties);
        User user = new User("budi@finpulse.app", "hash", "Budi Santoso");
        UUID userId = UUID.randomUUID();
        user.setId(userId);

        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.parseAndValidateAccessToken(token);

        assertThat(jwtService.extractUserId(claims)).isEqualTo(userId);
        assertThat(claims.get("email", String.class)).isEqualTo("budi@finpulse.app");
        assertThat(claims.get("fullName", String.class)).isEqualTo("Budi Santoso");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(jwtService.getAccessExpirationSeconds()).isEqualTo(900L);
    }

    @Test
    void parseAccessTokenShouldRejectExpiredToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(VALID_SECRET);
        properties.setAccessExpirationMs(-1_000L);

        JwtService jwtService = new JwtService(properties);
        User user = new User("budi@finpulse.app", "hash", "Budi Santoso");
        user.setId(UUID.randomUUID());

        String expiredToken = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.parseAndValidateAccessToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseAccessTokenShouldRejectTamperedToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(VALID_SECRET);
        JwtService jwtService = new JwtService(properties);

        User user = new User("budi@finpulse.app", "hash", "Budi Santoso");
        user.setId(UUID.randomUUID());
        String token = jwtService.generateAccessToken(user);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parseAndValidateAccessToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void constructorShouldRejectShortSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short-secret");

        assertThatThrownBy(() -> new JwtService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void generateRefreshTokenValueShouldReturnDistinctHighEntropyTokens() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(VALID_SECRET);
        JwtService jwtService = new JwtService(properties);

        String token1 = jwtService.generateRefreshTokenValue();
        String token2 = jwtService.generateRefreshTokenValue();

        assertThat(token1).isNotBlank().hasSizeGreaterThanOrEqualTo(64);
        assertThat(token2).isNotBlank().isNotEqualTo(token1);
    }

    @Test
    void extractUserIdShouldRejectMissingOrBlankSubject() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(VALID_SECRET);
        JwtService jwtService = new JwtService(properties);

        Claims missingSubject = Jwts.claims().build();
        assertThatThrownBy(() -> jwtService.extractUserId(missingSubject))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");

        Claims blankSubject = Jwts.claims().subject("   ").build();
        assertThatThrownBy(() -> jwtService.extractUserId(blankSubject))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");
    }
}
