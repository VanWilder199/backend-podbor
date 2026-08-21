package by.marketplace.car.mapper;

import by.marketplace.car.dto.CarDto;
import by.marketplace.jooq.tables.records.CarsRecord;
import org.springframework.stereotype.Component;

@Component
public class CarMapper {

    public static CarDto toDto(CarsRecord car) {
        return new CarDto(
                car.getId(),
                car.getVin(),
                car.getAvbyListingUrl(),
                car.getMake(),
                car.getModel(),
                car.getYear(),
                car.getListingStatus()
        );
    }
}
