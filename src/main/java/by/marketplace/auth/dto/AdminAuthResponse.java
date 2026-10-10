package by.marketplace.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"accessToken", "expiresIn"})
public record AdminAuthResponse(
        String accessToken,
        Long expiresIn
) {
}
