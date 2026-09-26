package by.marketplace.inspector.dto;

import jakarta.validation.constraints.NotBlank;

public record BanInspectorRequest(
        @NotBlank String reason
) {
}
