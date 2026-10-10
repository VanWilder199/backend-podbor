package by.marketplace.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"secret", "otpAuthUrl"})
public record AdminTotpSetupResponse(
        String secret,
        String otpAuthUrl
) {
}
