package by.marketplace.auth.service;

import by.marketplace.auth.dto.AuthResponse;
import io.jsonwebtoken.Claims;

import java.util.UUID;

public interface JwtService {

    long getAccessTokenExpiration();

    AuthResponse issueTokens(UUID userId, String email, String role);

    String generateAccessToken(UUID userId, String email, String role);

    String generateAccessToken(UUID userId, String email, String role, long expirationMinutes);

    String refreshToken(UUID userId);

    AuthResponse rotateRefreshToken(String oldToken);

    Claims validateAccessToken(String token);
}
