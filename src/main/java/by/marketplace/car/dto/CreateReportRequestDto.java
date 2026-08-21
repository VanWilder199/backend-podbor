package by.marketplace.car.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateReportRequestDto(
        @Email @NotBlank String email
) {
}
