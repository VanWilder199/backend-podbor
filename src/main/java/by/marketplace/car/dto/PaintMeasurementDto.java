package by.marketplace.car.dto;

import java.util.UUID;

public record PaintMeasurementDto(
        UUID id,
        String panelCode,
        String spot,
        int thicknessUm,
        String note
) {
}
