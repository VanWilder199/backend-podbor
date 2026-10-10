package by.marketplace.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(requiredProperties = {"adminId", "action", "createdAt"})
public record ModerationHistroyEntryDto(
        UUID adminId,
        String action,
        String reason,
        OffsetDateTime createdAt

) {
}
