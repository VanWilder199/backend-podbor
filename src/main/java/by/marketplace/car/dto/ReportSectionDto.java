package by.marketplace.car.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"id", "sectionKey", "orderNo", "summary", "items", "media"})
public record ReportSectionDto(
        UUID id,
        String sectionKey,
        int orderNo,
        String summary,
        List<SectionItemDto> items,
        List<ReportMediaDto> media
) {
}
