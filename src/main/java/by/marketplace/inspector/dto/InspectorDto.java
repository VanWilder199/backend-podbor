package by.marketplace.inspector.dto;

import java.util.UUID;

public record InspectorDto(
        UUID id,
        long telegramUserId,
        String fullName,
        String phone,
        String email,
        String status
) { }
