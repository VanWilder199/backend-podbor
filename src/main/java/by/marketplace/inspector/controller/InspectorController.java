package by.marketplace.inspector.controller;

import by.marketplace.car.service.CarService;
import by.marketplace.car.service.ReportService;
import by.marketplace.inspector.TelegramUser;
import by.marketplace.inspector.dto.*;
import by.marketplace.inspector.service.InspectorService;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
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

        if ("banned".equals(inspector.status())) {
            throw new AppException(ErrorCode.INSPECTOR_BANNED);
        }

        var cardId = carService.findOrCreateByUrl(request.avbyUrl());
        var createReport = reportService.createReport(inspector.id(), cardId);

       return ResponseEntity.ok(new CreateReportResponse(createReport));
    }


}