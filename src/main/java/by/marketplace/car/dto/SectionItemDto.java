package by.marketplace.car.dto;

import by.marketplace.car.enums.ItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"itemKey", "status"})
public record SectionItemDto(
        String itemKey,
        ItemStatus status,
        String comment
) {
}
