package by.marketplace.purchase.bepaid;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Тело исходящего POST /ctp/api/checkouts — зеркалит вложенную форму реального bePaid JSON
 * ({@code checkout.{transaction_type, test, settings.{notification_url,...}, order.{...}}}).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BePaidCheckoutRequest(Checkout checkout) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Checkout(String transactionType, boolean test, Settings settings, Order order) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Settings(String notificationUrl, String successUrl, String declineUrl,
                           String failUrl, String cancelUrl) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Order(String currency, long amount, String description, String trackingId) {
    }
}