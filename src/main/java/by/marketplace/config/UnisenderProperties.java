package by.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "unisender")
public record UnisenderProperties(
        String apiKey,
        @DefaultValue("ru") String region,
        @DefaultValue("") String smsSender,
        @DefaultValue("") String senderName,
        @DefaultValue("") String senderEmail,
        @DefaultValue("") String emailListId
) {
}
