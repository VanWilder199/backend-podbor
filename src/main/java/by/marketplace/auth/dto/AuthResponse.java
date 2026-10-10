package by.marketplace.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"accessToken", "refreshToken", "expiresIn"})
public record AuthResponse (
    String accessToken,
    String refreshToken,
    Long expiresIn
) {}
