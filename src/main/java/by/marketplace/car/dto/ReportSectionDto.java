package by.marketplace.car.dto;

import java.util.List;
import java.util.UUID;

public record ReportSectionDto(
        UUID id,
        String sectionKey,
        int orderNo,
        String summary,
        List<SectionItemDto> items,
        List<ReportMediaDto> media
) {
}
