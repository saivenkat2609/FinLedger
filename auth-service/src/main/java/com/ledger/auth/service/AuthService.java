package com.ledger.auth.service;

import com.ledger.auth.dto.AuthResponse;
import com.ledger.auth.dto.LoginRequest;
import com.ledger.auth.dto.RefreshTokenRequest;
import com.ledger.auth.dto.RegisterRequest;
import com.ledger.auth.entity.RefreshToken;
import com.ledger.auth.entity.User;
import com.ledger.auth.repository.RefreshTokenRepository;
import com.ledger.auth.repository.UserRepository;
import com.ledger.auth.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new IllegalArgumentException("Refresh token expired");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(stored.getUser());
        return new AuthResponse(newAccessToken, stored.getToken(), stored.getUser().getUsername());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.findByToken(request.getRefreshToken())
                .ifPresent(refreshTokenRepository::delete);
    }

    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshTokenStr = jwtTokenProvider.generateRefreshToken(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(refreshTokenStr);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, refreshTokenStr, user.getUsername());
    }
}
