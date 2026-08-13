package by.marketplace.inspector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramUserPayload(
        long id,
        @JsonProperty("first_name") String firstName,
        String username
) {
}
