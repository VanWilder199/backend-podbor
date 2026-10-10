package by.marketplace.inspector.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"id", "telegramUserId", "fullName", "phone", "email", "status"})
public record InspectorDto(
        UUID id,
        long telegramUserId,
        String fullName,
        String phone,
        String email,
        String status
) { }
