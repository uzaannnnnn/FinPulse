package com.finpulse.service;

import com.finpulse.dto.AuthResponse;
import com.finpulse.dto.LoginRequest;
import com.finpulse.dto.RefreshTokenRequest;
import com.finpulse.dto.RegisterRequest;
import com.finpulse.entity.RefreshToken;
import com.finpulse.entity.User;
import com.finpulse.exception.EmailAlreadyExistsException;
import com.finpulse.exception.InvalidCredentialsException;
import com.finpulse.exception.InvalidTokenException;
import com.finpulse.repository.RefreshTokenRepository;
import com.finpulse.repository.UserRepository;
import com.finpulse.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerShouldNormalizeEmailAndPersistUserAndToken() {
        RegisterRequest request = new RegisterRequest("  Budi Santoso ", "Budi@FinPulse.app", "Rahasia123!");
        UUID userId = UUID.randomUUID();

        when(userRepository.existsByEmailIgnoreCase("budi@finpulse.app")).thenReturn(false);
        when(passwordEncoder.encode("Rahasia123!")).thenReturn("$2a$10$encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(userId);
            saved.setCreatedAt(Instant.now());
            return saved;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-jwt");
        when(jwtService.generateRefreshTokenValue()).thenReturn("refresh-token-value");
        when(jwtService.calculateRefreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(604800));
        when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.user().email()).isEqualTo("budi@finpulse.app");
        assertThat(response.user().fullName()).isEqualTo("Budi Santoso");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void registerShouldThrowWhenEmailAlreadyExists() {
        when(userRepository.existsByEmailIgnoreCase("budi@finpulse.app")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Budi", "budi@finpulse.app", "Rahasia123!")))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void loginShouldThrowWhenPasswordDoesNotMatch() {
        User user = new User("budi@finpulse.app", "$2a$10$encoded", "Budi");
        when(userRepository.findByEmailIgnoreCase("budi@finpulse.app")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass!", "$2a$10$encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("budi@finpulse.app", "WrongPass!")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshTokenShouldRotateOldTokenAndIssueNewPair() {
        User user = new User("budi@finpulse.app", "$2a$10$encoded", "Budi");
        user.setId(UUID.randomUUID());
        user.setCreatedAt(Instant.now());

        RefreshToken oldToken = new RefreshToken(user, "old-refresh", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenForUpdate("old-refresh")).thenReturn(Optional.of(oldToken));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access");
        when(jwtService.generateRefreshTokenValue()).thenReturn("new-refresh");
        when(jwtService.calculateRefreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(604800));
        when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.refreshToken(new RefreshTokenRequest("old-refresh"));

        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refreshTokenShouldRejectRevokedToken() {
        User user = new User("budi@finpulse.app", "$2a$10$encoded", "Budi");
        RefreshToken revoked = new RefreshToken(user, "revoked-token", Instant.now().plusSeconds(3600));
        revoked.setRevoked(true);

        when(refreshTokenRepository.findByTokenForUpdate("revoked-token")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("revoked-token")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshTokenShouldMarkExpiredTokenRevokedAndThrow() {
        User user = new User("budi@finpulse.app", "$2a$10$encoded", "Budi");
        RefreshToken expired = new RefreshToken(user, "expired-token", Instant.now().minusSeconds(10));

        when(refreshTokenRepository.findByTokenForUpdate("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("expired-token")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("kedaluwarsa");

        assertThat(expired.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(expired);
    }

    @Test
    void logoutShouldOnlyRevokeAuthenticatedUserTokensIgnoringForeignToken() {
        UUID userId = UUID.randomUUID();
        authService.logout(userId, new RefreshTokenRequest("user-b-foreign-refresh-token"));

        verify(refreshTokenRepository).revokeAllActiveByUserId(userId);
        verifyNoMoreInteractions(refreshTokenRepository);
    }
}
