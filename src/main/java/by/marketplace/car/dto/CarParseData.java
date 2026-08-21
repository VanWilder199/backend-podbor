package by.marketplace.car.dto;

public record CarParseData(
        String vin,
        String make,
        String model,
        Integer year
) {
}
