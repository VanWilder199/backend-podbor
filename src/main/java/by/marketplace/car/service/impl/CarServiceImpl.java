package by.marketplace.car.service.impl;

import by.marketplace.car.AvByParser;
import by.marketplace.car.dto.CarDto;
import by.marketplace.car.dto.CarParseData;
import by.marketplace.car.mapper.CarMapper;
import by.marketplace.car.service.CarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

import static by.marketplace.jooq.Tables.CARS;
import static by.marketplace.jooq.Tables.REPORT_REQUESTS;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarServiceImpl implements CarService {

    private final DSLContext dslContext;
    private final AvByParser avByParser;

    @Override
    public UUID findOrCreateByUrl(String url) {
        Optional<UUID> existing = dslContext.select(CARS.ID)
                .from(CARS)
                .where(CARS.AVBY_LISTING_URL.eq(url))
                .fetchOptional(CARS.ID);

        if (existing.isPresent()) {
            log.debug("Car already exists: carId={}, url={}", existing.get(), url);
            return existing.get();
        }

        CarParseData parsedData = avByParser.parse(url);

        Optional<UUID> inserted = dslContext.insertInto(CARS)
                .set(CARS.VIN, parsedData.vin())
                .set(CARS.AVBY_LISTING_URL, url)
                .set(CARS.MAKE, parsedData.make())
                .set(CARS.MODEL, parsedData.model())
                .set(CARS.YEAR, parsedData.year())
                .onConflict(CARS.AVBY_LISTING_URL)
                .doNothing()
                .returning(CARS.ID)
                .fetchOptional(CARS.ID);

        if (inserted.isPresent()) {
            log.info("Car created from av.by: carId={}, url={}, vin={}",
                    inserted.get(), url, parsedData.vin());
            return inserted.get();
        }

        log.debug("Car insert race, fetching existing: url={}", url);

        return dslContext.select(CARS.ID)
                .from(CARS)
                .where(CARS.AVBY_LISTING_URL.eq(url))
                .fetchOne(CARS.ID);
    }

    @Override
    public Optional<CarDto> findByVin(String vin) {
        return dslContext.selectFrom(CARS)
                .where(CARS.VIN.eq(vin))
                .fetchOptional()
                .map(CarMapper::toDto);
    }

    @Override
    public Long createReportRequest(UUID buyerId, String email, UUID carId) {
        Optional<Long> inserted = dslContext.insertInto(REPORT_REQUESTS)
                .set(REPORT_REQUESTS.BUYER_ID, buyerId)
                .set(REPORT_REQUESTS.EMAIL, email)
                .set(REPORT_REQUESTS.CAR_ID, carId)
                .onConflict(REPORT_REQUESTS.CAR_ID, REPORT_REQUESTS.BUYER_ID)
                .doNothing()
                .returning(REPORT_REQUESTS.ID)
                .fetchOptional(REPORT_REQUESTS.ID);

        Long requestId = inserted.orElseGet(() ->
                dslContext.select(REPORT_REQUESTS.ID)
                        .from(REPORT_REQUESTS)
                        .where(REPORT_REQUESTS.CAR_ID.eq(carId).and(REPORT_REQUESTS.BUYER_ID.eq(buyerId)))
                        .fetchOne(REPORT_REQUESTS.ID));

        log.info("Report request created: requestId={}, carId={}, buyerId={}", requestId, carId, buyerId);

        return requestId;
    }
}
