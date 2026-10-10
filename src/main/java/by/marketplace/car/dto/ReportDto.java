package by.marketplace.car.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"id", "carId", "inspectorId", "versionNo", "status", "stopFactors", "sections", "paintMeasurements", "globalMedia"})
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
