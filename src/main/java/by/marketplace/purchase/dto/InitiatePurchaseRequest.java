package by.marketplace.purchase.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InitiatePurchaseRequest(
        @NotNull UUID reportId
) {
}
