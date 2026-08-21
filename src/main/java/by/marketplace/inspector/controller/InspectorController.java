package by.marketplace.inspector.controller;

import by.marketplace.car.service.CarService;
import by.marketplace.car.service.ReportService;
import by.marketplace.inspector.TelegramUser;
import by.marketplace.inspector.dto.CreateReportResponse;
import by.marketplace.inspector.dto.InspectorDto;
import by.marketplace.inspector.dto.RegisterCarReportRequest;
import by.marketplace.inspector.dto.RegisterInspectorRequest;
import by.marketplace.inspector.service.InspectorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inspector")
@RequiredArgsConstructor
public class InspectorController {

    private final InspectorService inspectorService;
    private final CarService carService;
    private final ReportService reportService;


    @PostMapping("/register")
    ResponseEntity<InspectorDto> register(
            @AuthenticationPrincipal TelegramUser telegramUser,
            @Valid @RequestBody RegisterInspectorRequest request
            ) {
        return ResponseEntity.ok(inspectorService.register(telegramUser, request));
    }

    @GetMapping("/")
    ResponseEntity<InspectorDto> inspector(
            @AuthenticationPrincipal TelegramUser telegramUser
    ) {
        return ResponseEntity.ok(inspectorService.findByTelegramId(telegramUser.id()));
    }

    @PostMapping("/reports")
    ResponseEntity<CreateReportResponse> reports(
            @AuthenticationPrincipal TelegramUser telegramUser,
            @Valid @RequestBody RegisterCarReportRequest request
    ) {
        var inspector = inspectorService.findByTelegramId(telegramUser.id());
        var cardId = carService.findOrCreateByUrl(request.avbyUrl());
        var createReport = reportService.createReport(inspector.id(), cardId);

       return ResponseEntity.ok(new CreateReportResponse(createReport));
    }


}
