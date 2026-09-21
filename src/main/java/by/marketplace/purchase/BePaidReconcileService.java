package by.marketplace.purchase;

import by.marketplace.jooq.tables.records.PurchasesRecord;
import by.marketplace.purchase.bepaid.BePaidCheckoutStatusResponse;
import by.marketplace.purchase.bepaid.BePaidClient;
import by.marketplace.purchase.service.PurchaseService;
import by.marketplace.shared.service.IdempotencyService;
import org.jooq.DSLContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static by.marketplace.jooq.Tables.PURCHASES;

@Component
public class BePaidReconcileService {
    private final DSLContext dsl;
    private final BePaidClient bePaidClient;
    private final IdempotencyService idempotencyService;
    private final PurchaseService purchaseService;

    public BePaidReconcileService(DSLContext dsl, BePaidClient bePaidClient, IdempotencyService idempotencyService, PurchaseService purchaseService) {
        this.dsl = dsl;
        this.bePaidClient = bePaidClient;
        this.idempotencyService = idempotencyService;
        this.purchaseService = purchaseService;
    }

    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void reconcile() {
        List<PurchasesRecord> purchases = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.STATUS.eq("pending"))
                .and(PURCHASES.CREATED_AT.lessThan(OffsetDateTime.now().minusHours(1)))
                .and(PURCHASES.BEPAID_CHECKOUT_TOKEN.isNotNull())
                .fetch();

        for (PurchasesRecord purchase : purchases) {

            BePaidCheckoutStatusResponse response = bePaidClient.getCheckoutStatus(purchase.getBepaidCheckoutToken());

            String status = response.checkout().gatewayResponse().payment().status();

            if (status.equals("successful") && idempotencyService.claim(response.checkout().gatewayResponse().payment().uid())) {
                purchaseService.applySuccessfulPayment(purchase);

            } else if (status.equals("failed") || status.equals("expired")) {
                    dsl.update(PURCHASES)
                            .set(PURCHASES.STATUS, "failed")
                            .where(PURCHASES.ID.eq(purchase.getId()))
                            .and(PURCHASES.STATUS.eq("pending"))
                            .execute();

            }
        }

    }
}
