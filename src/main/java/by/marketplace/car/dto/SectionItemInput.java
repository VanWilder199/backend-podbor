package by.marketplace.car.dto;

import by.marketplace.car.enums.ItemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SectionItemInput(
        @NotBlank String itemKey,
        @NotNull ItemStatus status,
        String comment
        ) {
}
