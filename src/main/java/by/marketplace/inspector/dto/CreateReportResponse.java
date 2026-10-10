package by.marketplace.inspector.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"reportId"})
public record CreateReportResponse(
        UUID reportId
) {
}
