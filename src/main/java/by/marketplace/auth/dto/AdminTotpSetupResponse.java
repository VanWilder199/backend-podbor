package by.marketplace.auth.dto;

public record AdminTotpSetupResponse(
        String secret,
        String otpAuthUrl
) {
}
