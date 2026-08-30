package by.marketplace.car.dto;


import by.marketplace.car.enums.ItemStatus;

public record SectionItemDto(
        String itemKey,
        ItemStatus status,
        String comment
) {
}
