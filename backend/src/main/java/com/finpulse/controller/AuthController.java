package com.finpulse.controller;

import com.finpulse.dto.AuthResponse;
import com.finpulse.dto.LoginRequest;
import com.finpulse.dto.RefreshTokenRequest;
import com.finpulse.dto.RegisterRequest;
import com.finpulse.dto.UserDto;
import com.finpulse.security.FinPulseUserPrincipal;
import com.finpulse.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for authentication, JWT lifecycle, and active user profile retrieval.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registrasi, login, refresh token, logout, dan profil pengguna aktif")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Registrasi akun pengguna baru")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login pengguna dengan email dan password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Perbarui Access Token menggunakan Refresh Token")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Logout dan mencabut Refresh Token pengguna")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @RequestBody(required = false) RefreshTokenRequest request
    ) {
        authService.logout(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Ambil profil pengguna yang sedang terautentikasi")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal FinPulseUserPrincipal principal) {
        return ResponseEntity.ok(authService.getCurrentUser(principal.getId()));
    }
}
