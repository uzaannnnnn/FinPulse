package com.finpulse.service;

import com.finpulse.dto.AuthResponse;
import com.finpulse.dto.LoginRequest;
import com.finpulse.dto.RefreshTokenRequest;
import com.finpulse.dto.RegisterRequest;
import com.finpulse.dto.UserDto;
import com.finpulse.entity.RefreshToken;
import com.finpulse.entity.User;
import com.finpulse.exception.EmailAlreadyExistsException;
import com.finpulse.exception.InvalidCredentialsException;
import com.finpulse.exception.InvalidTokenException;
import com.finpulse.exception.ResourceNotFoundException;
import com.finpulse.repository.RefreshTokenRepository;
import com.finpulse.repository.UserRepository;
import com.finpulse.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Handles user registration, password login, refresh token rotation, logout revocation, and profile lookup.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyExistsException("Email sudah terdaftar");
        }

        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.fullName().trim()
        );

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyExistsException("Email sudah terdaftar");
        }

        return issueAuthTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Email atau password salah"));

        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Email atau password salah");
        }

        return issueAuthTokens(user);
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenValue = request.refreshToken().trim();
        RefreshToken storedToken = refreshTokenRepository.findByTokenForUpdate(tokenValue)
                .orElseThrow(() -> new InvalidTokenException("Refresh token tidak valid"));

        if (storedToken.isRevoked()) {
            throw new InvalidTokenException("Refresh token sudah dicabut");
        }

        if (!storedToken.getExpiresAt().isAfter(Instant.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new InvalidTokenException("Refresh token telah kedaluwarsa");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        return issueAuthTokens(storedToken.getUser());
    }

    @Transactional
    public void logout(UUID userId, RefreshTokenRequest request) {
        refreshTokenRepository.revokeAllActiveByUserId(userId);
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna tidak ditemukan"));
        return UserDto.from(user);
    }

    private AuthResponse issueAuthTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = jwtService.generateRefreshTokenValue();
        Instant refreshExpiry = jwtService.calculateRefreshTokenExpiry();

        RefreshToken refreshToken = new RefreshToken(user, refreshTokenValue, refreshExpiry);
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.bearer(
                accessToken,
                refreshTokenValue,
                jwtService.getAccessExpirationSeconds(),
                UserDto.from(user)
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
