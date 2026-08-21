package by.marketplace.car.dto;

import java.util.UUID;

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
