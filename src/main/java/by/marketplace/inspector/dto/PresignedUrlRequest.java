package by.marketplace.inspector.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record PresignedUrlRequest(
        UUID sectionId,
        @NotBlank String fileName,
        @NotBlank String contentType
) {
}
