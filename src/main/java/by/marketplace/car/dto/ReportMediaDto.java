package by.marketplace.car.dto;

import java.util.UUID;

public record ReportMediaDto(
        UUID id,
        String kind,
        String s3Key,
        String status,
        int orderNo
) {
}
