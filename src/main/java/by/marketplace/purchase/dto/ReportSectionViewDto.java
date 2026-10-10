package by.marketplace.purchase.dto;

import by.marketplace.car.dto.SectionItemDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"id", "sectionKey", "orderNo", "summary", "items", "media"})
public record ReportSectionViewDto(
        UUID id,
        String sectionKey,
        int orderNo,
        String summary,
        List<SectionItemDto> items,
        List<ReportMediaViewDto> media
) {
}
