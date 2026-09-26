package by.marketplace.purchase.service;

import by.marketplace.purchase.dto.PayoutBatch;

import java.util.List;
import java.util.UUID;

public interface PayoutService {
    void createPayout(UUID purchaseId, UUID inspectorId, long grossAmountByn);
    void markAsPaid(UUID payoutId, UUID adminId);
    List<PayoutBatch> listBatches(String status);
}
