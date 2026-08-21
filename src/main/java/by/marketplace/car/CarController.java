package by.marketplace.car;

import by.marketplace.car.dto.CarDto;
import by.marketplace.car.dto.CreateReportRequestDto;
import by.marketplace.car.service.CarService;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/cars")
public class CarController {
    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @GetMapping("/search")
    public ResponseEntity<CarDto> getCarByVin(@RequestParam("vin") String vin) {
        CarDto car = carService.findByVin(vin)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        return ResponseEntity.ok(car);
    }


    @PostMapping("/{cardId}")
    public Long createReport(
            @AuthenticationPrincipal UUID userId,
            @RequestBody @Valid CreateReportRequestDto reportRequestDto,
            @PathVariable UUID cardId){
        return carService.createReportRequest(userId, reportRequestDto.email(), cardId);
    }
}
