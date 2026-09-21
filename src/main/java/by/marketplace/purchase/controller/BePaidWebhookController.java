package by.marketplace.purchase.controller;

import by.marketplace.purchase.service.PurchaseService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
public class BePaidWebhookController {
    private final PurchaseService purchaseService;

    public BePaidWebhookController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }


    @PostMapping("/bepaid")
    public void bePaidWebhook(@RequestBody String rawBody, @RequestHeader(value = "Authorization", required = false) String authorization) {
        purchaseService.handleWebhook(rawBody, authorization);
    }
}
