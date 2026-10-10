package by.marketplace.purchase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"id", "kind", "status", "orderNo"})
public record ReportMediaViewDto(
        UUID id,
        String kind,
        String url,
        String status,
        int orderNo
) {
}
