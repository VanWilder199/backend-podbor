package by.marketplace.inspector.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"uploadUrl", "s3Key"})
public record PresignedUrlResponse(
        String uploadUrl,
        String s3Key
) {
}
