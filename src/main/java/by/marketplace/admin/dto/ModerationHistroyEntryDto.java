package by.marketplace.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ModerationHistroyEntryDto(
        UUID adminId,
        String action,
        String reason,
        OffsetDateTime createdAt

) {
}
