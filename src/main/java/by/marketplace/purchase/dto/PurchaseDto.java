package by.marketplace.purchase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(requiredProperties = {"id", "reportId", "amountByn", "status", "createdAt"})
public record PurchaseDto(
        UUID id,
        UUID reportId,
        Long amountByn,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt
) {
}
