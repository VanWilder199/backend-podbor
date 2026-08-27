package by.marketplace.inspector.dto;

public record PresignedUrlResponse(
        String uploadUrl,
        String s3Key
) {
}
