package by.marketplace.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        @NotBlank
        String botToken,
        @NotNull
        Duration maxAge
) {}
