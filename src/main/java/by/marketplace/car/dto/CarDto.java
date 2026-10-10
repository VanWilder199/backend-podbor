package by.marketplace.car.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"id", "vin", "make", "model"})
public record CarDto(
        UUID id,
        String vin,
        String avbyListingUrl,
        String make,
        String model,
        Integer year,
        String listingStatus

) {
}
