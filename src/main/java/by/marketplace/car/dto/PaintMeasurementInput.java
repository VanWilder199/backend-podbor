package by.marketplace.car.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaintMeasurementInput(
        @NotNull UUID panelId,
        String spot,
        @NotNull Integer thicknessUm,
        String note
) {
}
