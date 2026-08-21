package by.marketplace.inspector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterCarReportRequest (
        @NotNull @NotBlank String avbyUrl
){
}
