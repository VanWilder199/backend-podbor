package by.marketplace.inspector.controller;

import by.marketplace.car.dto.ReportDto;
import by.marketplace.car.dto.UpdateConclusionRequest;
import by.marketplace.car.dto.UpdatePaintMeasurementsRequest;
import by.marketplace.car.dto.UpdateSectionRequest;
import by.marketplace.car.service.ReportService;
import by.marketplace.inspector.TelegramUser;
import by.marketplace.inspector.service.InspectorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("inspector/reports")
public class ReportController {

    private final ReportService reportService;
    private final InspectorService inspectorService;

    public ReportController(ReportService reportService, InspectorService inspectorService) {
        this.reportService = reportService;
        this.inspectorService = inspectorService;
    }


    @PutMapping("/{reportId}/sections/{sectionId}")
    ResponseEntity<Void> updateSection(
            @PathVariable UUID reportId,
            @PathVariable UUID sectionId,
            @AuthenticationPrincipal TelegramUser telegramUser,
            @Valid @RequestBody UpdateSectionRequest request
    ) {
        var inspectorId = inspectorService.findByTelegramId(telegramUser.id()).id();

        reportService.updateSection(reportId,sectionId, inspectorId, request);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{reportId}/paint")
    ResponseEntity<Void> updatePaintMeasurements(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal TelegramUser telegramUser,
            @Valid @RequestBody UpdatePaintMeasurementsRequest request
    ) {
        var inspectorId = inspectorService.findByTelegramId(telegramUser.id()).id();

        reportService.updatePaintMeasurements(reportId, inspectorId, request);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{reportId}/conclusion")
    ResponseEntity<Void> updateConclusion(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal TelegramUser telegramUser,
            @Valid @RequestBody UpdateConclusionRequest request
    ) {
        var inspectorId = inspectorService.findByTelegramId(telegramUser.id()).id();

        reportService.updateConclusion(reportId, inspectorId, request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{reportId}/submit")
    ResponseEntity<Void> submitForModeration(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal TelegramUser telegramUser
    ) {
        var inspectorId = inspectorService.findByTelegramId(telegramUser.id()).id();

        reportService.submitForModeration(reportId, inspectorId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{reportId}")
    ResponseEntity<ReportDto> getReport(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal TelegramUser telegramUser
    ) {
        var inspectorId = inspectorService.findByTelegramId(telegramUser.id()).id();

        return ResponseEntity.ok(reportService.getReport(reportId, inspectorId));
    }
}
