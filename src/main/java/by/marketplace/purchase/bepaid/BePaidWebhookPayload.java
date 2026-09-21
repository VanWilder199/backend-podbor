package by.marketplace.purchase.bepaid;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Тело входящего вебхука bePaid на notification_url — {@code {"transaction": {"uid", "status", ...}}}.
 * Статусы: "successful" / "failed" / "expired" / ...
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BePaidWebhookPayload(Transaction transaction) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Transaction(String uid, String status, long amount, String currency, String trackingId) {
    }
}