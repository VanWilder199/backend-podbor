package by.marketplace.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(requiredProperties = {"reportId", "carId", "vin", "make", "model", "inspectorId", "versionNo", "submittedAt"})
public record ModerationQueueItemDto(
        UUID reportId,
        UUID carId,
        String vin,
        String make,
        String model,
        Integer year,
        UUID inspectorId,
        int versionNo,
        OffsetDateTime submittedAt
) {
}
