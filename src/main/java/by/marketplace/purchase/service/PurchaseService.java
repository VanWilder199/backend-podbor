package by.marketplace.purchase.service;

import by.marketplace.jooq.tables.records.PurchasesRecord;
import by.marketplace.purchase.dto.InitiatePurchaseResponse;
import by.marketplace.purchase.dto.PurchaseDto;

import java.util.List;
import java.util.UUID;

public interface PurchaseService {
    InitiatePurchaseResponse initiatePurchase(UUID buyerId, UUID reportId);
    void handleWebhook(String rawBody, String authorizationHeader);
    List<PurchaseDto> getPurchases(UUID buyerId);

    // общая логика "применить успешный платёж" — вызывается и handleWebhook (пуш от bePaid),
    // и BePaidReconcileService (опрос статуса), чтобы не дублировать payout/access-token/уведомление
    void applySuccessfulPayment(PurchasesRecord purchase);
}
