package by.marketplace.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminSetupTotpRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
