package by.marketplace.inspector.controller;

import by.marketplace.inspector.dto.BanInspectorRequest;
import by.marketplace.inspector.dto.InspectorDto;
import by.marketplace.inspector.service.InspectorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/inspectors")
@RequiredArgsConstructor
public class AdminInspectorController {

    private final InspectorService inspectorService;

    @GetMapping
    public ResponseEntity<List<InspectorDto>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(inspectorService.listInspectors(status));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<InspectorDto> verify(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID adminId
    ) {
        return ResponseEntity.ok(inspectorService.verifyInspector(id, adminId));
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<InspectorDto> ban(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestBody BanInspectorRequest body
    ) {
        return ResponseEntity.ok(inspectorService.banInspector(id, adminId, body.reason()));
    }
}