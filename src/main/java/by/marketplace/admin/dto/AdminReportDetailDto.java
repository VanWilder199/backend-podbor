package by.marketplace.admin.dto;

import by.marketplace.car.dto.ReportDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(requiredProperties = {"report", "history"})
public record AdminReportDetailDto (
        ReportDto report,
        List<ModerationHistroyEntryDto> history
) {
}
