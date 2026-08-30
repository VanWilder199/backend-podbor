package by.marketplace.car.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdatePaintMeasurementsRequest(
        @NotNull @Valid List<PaintMeasurementInput> measurements
) {
}
