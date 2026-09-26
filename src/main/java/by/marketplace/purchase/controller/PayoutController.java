package by.marketplace.purchase.controller;

import by.marketplace.purchase.dto.PayoutBatch;
import by.marketplace.purchase.service.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/payouts")
@RequiredArgsConstructor
public class PayoutController {
    private final PayoutService payoutService;

    @GetMapping
    public ResponseEntity<List<PayoutBatch>> listBatches(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(payoutService.listBatches(status));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<Void> markAsPaid(@PathVariable UUID id, @AuthenticationPrincipal UUID adminId) {
        payoutService.markAsPaid(id, adminId);
        return ResponseEntity.ok().build();
    }
}