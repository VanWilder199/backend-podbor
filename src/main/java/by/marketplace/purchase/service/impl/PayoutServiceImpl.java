package by.marketplace.purchase.service.impl;

import by.marketplace.purchase.dto.PayoutBatch;
import by.marketplace.purchase.service.PayoutService;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;

@Slf4j
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

        log.info("Payout accrued: purchaseId={}, inspectorId={}, grossAmountByn={}, netAmountByn={}",
                purchaseId, inspectorId, grossAmountByn, amount);
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

       log.info("Payout batch marked paid: batchId={}, adminId={}", payoutId, adminId);
    }

    @Override
    public List<PayoutBatch> listBatches(String status) {
        Condition condition = status == null ? DSL.noCondition() : PAYOUT_BATCHES.STATUS.eq(status);

        return dsl.select(
                        PAYOUT_BATCHES.ID,
                        PAYOUT_BATCHES.INSPECTOR_ID,
                        INSPECTORS.FULL_NAME,
                        INSPECTORS.EMAIL,
                        PAYOUT_BATCHES.PERIOD_START,
                        PAYOUT_BATCHES.PERIOD_END,
                        PAYOUT_BATCHES.AMOUNT_BYN,
                        PAYOUT_BATCHES.STATUS,
                        PAYOUT_BATCHES.CREATED_AT,
                        PAYOUT_BATCHES.PAID_AT
                )
                .from(PAYOUT_BATCHES)
                .join(INSPECTORS).on(PAYOUT_BATCHES.INSPECTOR_ID.eq(INSPECTORS.ID))
                .where(condition)
                .orderBy(PAYOUT_BATCHES.CREATED_AT.desc())
                .fetch(r -> new PayoutBatch(
                        r.get(PAYOUT_BATCHES.ID),
                        r.get(PAYOUT_BATCHES.INSPECTOR_ID),
                        r.get(INSPECTORS.FULL_NAME),
                        r.get(INSPECTORS.EMAIL),
                        r.get(PAYOUT_BATCHES.PERIOD_START),
                        r.get(PAYOUT_BATCHES.PERIOD_END),
                        r.get(PAYOUT_BATCHES.AMOUNT_BYN),
                        r.get(PAYOUT_BATCHES.STATUS),
                        r.get(PAYOUT_BATCHES.CREATED_AT),
                        r.get(PAYOUT_BATCHES.PAID_AT)
                ));
    }
}
