package by.marketplace.car.dto;

import java.util.List;
import java.util.UUID;

public record ReportDto(
        UUID id,
        UUID carId,
        UUID inspectorId,
        int versionNo,
        String status,
        Long priceByn,
        String conclusionText,
        List<String> stopFactors,
        List<ReportSectionDto> sections,
        List<PaintMeasurementDto> paintMeasurements,
        List<ReportMediaDto> globalMedia
) {
}
