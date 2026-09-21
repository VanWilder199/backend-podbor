package by.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "bepaid")
public record BePaidProperties(
        String shopId,
        String secretKey,
        String webhookSecret,
        @DefaultValue("true") boolean testMode
) {
}
