package by.marketplace.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteReportRequest(
        @NotBlank String reasonText
) {
}
