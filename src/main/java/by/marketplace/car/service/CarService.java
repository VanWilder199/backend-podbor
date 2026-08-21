package by.marketplace.car.service;

import by.marketplace.car.dto.CarDto;

import java.util.Optional;
import java.util.UUID;

public interface CarService {
    UUID findOrCreateByUrl(String url);
    Optional<CarDto> findByVin(String vin);
    Long createReportRequest(UUID buyerId, String email, UUID carId);
}
