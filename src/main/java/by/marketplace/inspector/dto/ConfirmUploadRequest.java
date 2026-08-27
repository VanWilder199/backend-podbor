package by.marketplace.inspector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmUploadRequest(
        UUID sectionId,
        @NotBlank String s3key,
        @NotNull MediaKind kind
) {
}
