package by.marketplace.car.dto;

import java.util.UUID;

public record MeasurementWithPanel(
        UUID id,
          UUID panelId,
          String spot,
          Integer thicknessUm,
          String note,
          String panelCode
) { }
