package by.marketplace.purchase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(requiredProperties = {"uuid", "inspectorId", "inspectorName", "inspectorEmail", "periodStart", "periodEnd", "amountByn", "status", "createdAt"})
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
