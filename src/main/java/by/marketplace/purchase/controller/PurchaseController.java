package by.marketplace.purchase.controller;


import by.marketplace.purchase.dto.InitiatePurchaseRequest;
import by.marketplace.purchase.dto.InitiatePurchaseResponse;
import by.marketplace.purchase.dto.PurchaseDto;
import by.marketplace.purchase.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/purchases")
public class PurchaseController {
    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }


    @GetMapping("")
    public ResponseEntity<List<PurchaseDto>> getPurchases(@AuthenticationPrincipal UUID buyerId) {
        return ResponseEntity.ok(purchaseService.getPurchases(buyerId));
    }

    @PostMapping("")
    public ResponseEntity<InitiatePurchaseResponse> createPurchase(@Valid @RequestBody InitiatePurchaseRequest request, @AuthenticationPrincipal UUID buyerId) {

        return ResponseEntity.ok(purchaseService.initiatePurchase(buyerId, request.reportId()));
    }

}
