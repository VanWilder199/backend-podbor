package by.marketplace.purchase.dto;

import by.marketplace.car.dto.PaintMeasurementDto;

import java.util.List;
import java.util.UUID;

public record ReportViewDto(
        UUID id,
        UUID carId,
        Long priceByn,
        String conclusionText,
        List<String> stopFactors,
        List<ReportSectionViewDto> sections,
        List<PaintMeasurementDto> paintMeasurements,
        List<ReportMediaViewDto> globalMedia
) {
}
