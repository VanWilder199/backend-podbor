package by.marketplace.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        @NotBlank String totpCode
) {
}
