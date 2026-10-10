package by.marketplace.car.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"id", "kind", "s3Key", "status", "orderNo"})
public record ReportMediaDto(
        UUID id,
        String kind,
        String s3Key,
        String status,
        int orderNo
) {
}
