package by.marketplace.purchase.dto;

import java.util.UUID;

public record ReportMediaViewDto(
        UUID id,
        String kind,
        String url,
        String status,
        int orderNo
) {
}
