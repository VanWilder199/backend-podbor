package by.marketplace.purchase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"purchaseId", "redirectUrl"})
public record InitiatePurchaseResponse(
        UUID purchaseId,
        String redirectUrl
) {
}
