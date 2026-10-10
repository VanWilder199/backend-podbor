package by.marketplace.car.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"id", "panelId", "spot", "panelCode"})
public record MeasurementWithPanel(
        UUID id,
          UUID panelId,
          String spot,
          Integer thicknessUm,
          String note,
          String panelCode
) { }
