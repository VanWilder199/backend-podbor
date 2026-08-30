package by.marketplace.car.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateConclusionRequest(
        @NotBlank String conclusionText,
        @NotNull Long priceByn
) {
}
