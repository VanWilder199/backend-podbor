package by.marketplace.purchase.bepaid;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Ответ bePaid на GET /ctp/api/checkouts/{token} — используется BePaidReconcileService.
 * Вложенная форма {@code checkout.gateway_response.payment.{uid, status, amount, currency}}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BePaidCheckoutStatusResponse(Checkout checkout) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Checkout(String token, GatewayResponse gatewayResponse) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GatewayResponse(Payment payment) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payment(String uid, String status, long amount, String currency) {
    }
}