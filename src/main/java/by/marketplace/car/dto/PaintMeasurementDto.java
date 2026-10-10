package by.marketplace.car.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"id", "panelCode", "spot", "thicknessUm"})
public record PaintMeasurementDto(
        UUID id,
        String panelCode,
        String spot,
        int thicknessUm,
        String note
) {
}
