package by.marketplace.purchase.bepaid;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Ответ bePaid на создание чекаута — из него берётся {@code checkout.token} для redirect-страницы
 * покупателя и для сохранения в {@code purchases.bepaid_checkout_token}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BePaidCheckoutResponse(Checkout checkout) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Checkout(String token) {
    }
}