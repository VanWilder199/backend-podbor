package by.marketplace.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviseReportRequest(
        @NotBlank String reasonText
) {
}
