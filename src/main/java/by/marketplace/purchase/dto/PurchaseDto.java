package by.marketplace.purchase.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseDto(
        UUID id,
        UUID reportId,
        Long amountByn,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt
) {
}
