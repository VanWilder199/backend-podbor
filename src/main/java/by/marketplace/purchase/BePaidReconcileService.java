package by.marketplace.purchase;

import by.marketplace.jooq.tables.records.PurchasesRecord;
import by.marketplace.purchase.bepaid.BePaidCheckoutStatusResponse;
import by.marketplace.purchase.bepaid.BePaidClient;
import by.marketplace.purchase.service.PurchaseService;
import by.marketplace.shared.logging.RequestLoggingFilter;
import by.marketplace.shared.service.IdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static by.marketplace.jooq.Tables.PURCHASES;

@Slf4j
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
        MDC.put("job", "bepaid-reconcile");
        MDC.put(RequestLoggingFilter.TRACE_ID, UUID.randomUUID().toString());

        long start = System.nanoTime();
        int paid = 0, failed = 0, stillPending = 0, errors = 0;

        try {
            List<PurchasesRecord> purchases = dsl.selectFrom(PURCHASES)
                    .where(PURCHASES.STATUS.eq("pending"))
                    .and(PURCHASES.CREATED_AT.lessThan(OffsetDateTime.now().minusHours(1)))
                    .and(PURCHASES.BEPAID_CHECKOUT_TOKEN.isNotNull())
                    .fetch();

            log.info("Reconcile started: candidates={}", purchases.size());

            for (PurchasesRecord purchase : purchases) {
                try {
                    log.debug("Reconcile checking purchase: purchaseId={}, createdAt={}",
                            purchase.getId(), purchase.getCreatedAt());
                    BePaidCheckoutStatusResponse response = bePaidClient.getCheckoutStatus(purchase.getBepaidCheckoutToken());

                    if (response.checkout().gatewayResponse() == null
                            || response.checkout().gatewayResponse().payment() == null) {
                        log.warn("Reconcile: bePaid returned no payment, skipping: purchaseId={}", purchase.getId());
                        stillPending++;
                        continue;
                    }

                    String status = response.checkout().gatewayResponse().payment().status();
                    String uid = response.checkout().gatewayResponse().payment().uid();

                    if (status.equals("successful")) {
                        if (idempotencyService.claim(uid)) {
                            purchaseService.applySuccessfulPayment(purchase);
                            log.info("Reconcile applied payment: purchaseId={}, bepaidUid={}",
                                    purchase.getId(), uid);
                            paid++;
                        } else {
                            log.debug("Reconcile: already applied by webhook: purchaseId={}, bepaidUid={}",
                                    purchase.getId(), uid);
                        }
                    } else if (status.equals("failed") || status.equals("expired")) {
                        dsl.update(PURCHASES)
                                .set(PURCHASES.STATUS, "failed")
                                .where(PURCHASES.ID.eq(purchase.getId()))
                                .and(PURCHASES.STATUS.eq("pending"))
                                .execute();
                        log.info("Reconcile marked purchase failed: purchaseId={}, bepaidStatus={}",
                                purchase.getId(), status);
                        failed++;
                    } else {
                        log.debug("Reconcile: still pending: purchaseId={}, bepaidStatus={}",
                                purchase.getId(), status);
                        stillPending++;
                    }
                } catch (RuntimeException e) {
                    errors++;
                    log.warn("Reconcile failed for purchase: purchaseId={}", purchase.getId(), e);
                }
            }
        } finally {
            if (errors > 0) {
                log.warn("Reconcile finished with errors: paid={} failed={} stillPending={} errors={} durationMs={}",
                        paid, failed, stillPending, errors, elapsedMs(start));
            } else {
                log.info("Reconcile finished: paid={} failed={} stillPending={} errors={} durationMs={}",
                        paid, failed, stillPending, errors, elapsedMs(start));
            }
            MDC.clear();
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}