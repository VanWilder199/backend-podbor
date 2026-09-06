package by.marketplace.admin.dto;

import by.marketplace.car.dto.ReportDto;

import java.util.List;

public record AdminReportDetailDto (
        ReportDto report,
        List<ModerationHistroyEntryDto> history
) {
}
