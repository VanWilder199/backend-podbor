package by.marketplace.purchase.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PayoutBatch(
        UUID uuid,
        UUID inspectorId,
        String inspectorName,
        String inspectorEmail,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd,
        long amountByn,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt
) {
}
