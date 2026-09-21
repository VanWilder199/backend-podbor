package by.marketplace.purchase.service;

import java.util.UUID;

public interface PayoutService {
    void createPayout(UUID purchaseId, UUID inspectorId, long grossAmountByn);
    void markAsPaid(UUID payoutId, UUID adminId);
}
