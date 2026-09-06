package by.marketplace.admin.controller;

import by.marketplace.admin.dto.AdminReportDetailDto;
import by.marketplace.admin.dto.DeleteReportRequest;
import by.marketplace.admin.dto.ModerationQueueItemDto;
import by.marketplace.admin.dto.ReviseReportRequest;
import by.marketplace.admin.service.ModerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/reports")
public class ModerationController {
    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }


    @GetMapping("/queue")
    public ResponseEntity<List<ModerationQueueItemDto>> getModerationQueue() {
        return ResponseEntity.ok(moderationService.getQueue());
    }


    @GetMapping("/{id}")
    public ResponseEntity<AdminReportDetailDto> getReportDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(moderationService.getReportDetail(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approveReport(@PathVariable UUID id, @AuthenticationPrincipal UUID adminId) {
        moderationService.approve(id, adminId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/revise")
    public ResponseEntity<Void> revision(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestBody ReviseReportRequest body
    ) {
        moderationService.requestRevision(id, adminId, body.reasonText());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> deleteReport(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestBody DeleteReportRequest body
    ) {
        moderationService.delete(id, adminId, body.reasonText());
        return ResponseEntity.ok().build();
    }
}
