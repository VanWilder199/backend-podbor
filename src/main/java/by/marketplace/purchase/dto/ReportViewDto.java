package by.marketplace.purchase.dto;

import by.marketplace.car.dto.PaintMeasurementDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"id", "carId", "stopFactors", "sections", "paintMeasurements", "globalMedia"})
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
