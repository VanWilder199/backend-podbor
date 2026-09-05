package by.marketplace.auth.dto;

import java.util.UUID;

public record AdminDto(
        UUID id,
        String email
) {
}
