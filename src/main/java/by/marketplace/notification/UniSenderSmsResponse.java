package by.marketplace.notification;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

public record UniSenderSmsResponse(
        Result result,
        String error,
        String code
) {
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public record Result(
            Long smsId
    ) {}
}
