package by.marketplace.inspector.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterInspectorRequest(
        @NotBlank String fullName,
        @NotNull String phone,
        @Email String email
) {
}
