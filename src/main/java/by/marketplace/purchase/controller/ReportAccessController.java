package by.marketplace.purchase.controller;

import by.marketplace.purchase.dto.ReportViewDto;
import by.marketplace.purchase.service.ReportAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportAccessController {
    private final ReportAccessService reportAccessService;

    @GetMapping("/view")
    public ResponseEntity<ReportViewDto> view(@RequestParam String token) {
        return ResponseEntity.ok(reportAccessService.getReportByToken(token));
    }
 }
