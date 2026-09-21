package by.marketplace.purchase.dto;

import java.util.UUID;

public record InitiatePurchaseResponse(
        UUID purchaseId,
        String redirectUrl
) {
}
