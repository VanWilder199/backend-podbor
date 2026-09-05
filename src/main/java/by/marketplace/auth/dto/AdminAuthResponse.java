package by.marketplace.auth.dto;

public record AdminAuthResponse(
        String accessToken,
        Long expiresIn
) {
}
