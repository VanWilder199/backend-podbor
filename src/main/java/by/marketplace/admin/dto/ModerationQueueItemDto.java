package by.marketplace.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

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
