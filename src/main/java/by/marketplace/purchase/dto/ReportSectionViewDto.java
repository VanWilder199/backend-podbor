package by.marketplace.purchase.dto;

import by.marketplace.car.dto.SectionItemDto;

import java.util.List;
import java.util.UUID;

public record ReportSectionViewDto(
        UUID id,
        String sectionKey,
        int orderNo,
        String summary,
        List<SectionItemDto> items,
        List<ReportMediaViewDto> media
) {
}
