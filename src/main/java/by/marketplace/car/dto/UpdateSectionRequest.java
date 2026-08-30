package by.marketplace.car.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateSectionRequest(
        @NotBlank String summary,
        @NotNull @Valid List<SectionItemInput> items
        ) {
}
