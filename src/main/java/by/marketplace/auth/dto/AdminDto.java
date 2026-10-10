package by.marketplace.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"id", "email"})
public record AdminDto(
        UUID id,
        String email
) {
}
