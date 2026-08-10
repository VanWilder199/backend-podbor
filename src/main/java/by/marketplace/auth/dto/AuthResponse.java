package by.marketplace.auth.dto;

public record AuthResponse (
    String accessToken,
    String refreshToken,
    Long expiresIn
) {}
