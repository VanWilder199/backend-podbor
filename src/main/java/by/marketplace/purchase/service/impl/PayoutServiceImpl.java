package by.marketplace.purchase.service.impl;

import by.marketplace.purchase.service.PayoutService;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;

@Service
public class PayoutServiceImpl implements PayoutService {
    private DSLContext dsl;

    public PayoutServiceImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void createPayout(UUID purchaseId, UUID inspectorId, long grossAmountByn) {
        long amount = grossAmountByn * 80 / 100;

        dsl.insertInto(INSPECTOR_PAYOUTS)
                .set(INSPECTOR_PAYOUTS.PURCHASE_ID, purchaseId)
                .set(INSPECTOR_PAYOUTS.INSPECTOR_ID, inspectorId)
                .set(INSPECTOR_PAYOUTS.AMOUNT_BYN, amount)
                .execute();
    }

    @Override
    public void markAsPaid(UUID payoutId, UUID adminId) {
       int countUpdatedRows =  dsl.update(PAYOUT_BATCHES)
                .set(PAYOUT_BATCHES.STATUS, "paid")
                .set(PAYOUT_BATCHES.PAID_AT, OffsetDateTime.now())
                .where(PAYOUT_BATCHES.ID.eq(payoutId))
                .and(PAYOUT_BATCHES.STATUS.eq("pending"))
                .execute();

       if ((countUpdatedRows == 0)) {
           throw new AppException(ErrorCode.PAYOUT_NOT_FOUND);
       }

    }
}
