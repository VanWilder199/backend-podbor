package by.marketplace.purchase;

import by.marketplace.auth.dto.Channel;
import by.marketplace.notification.NotificationSender;
import by.marketplace.shared.logging.RequestLoggingFilter;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;

@Slf4j
@Component
public class PayoutBatchScheduler {
    private final DSLContext dsl;
    private final NotificationSender notificationSender;

    public PayoutBatchScheduler(DSLContext dslContext, NotificationSender notificationSender) {
        this.dsl = dslContext;
        this.notificationSender = notificationSender;
    }

    @Scheduled(cron = "0 0 0 1 * ?")
    @Transactional
    public void createMonthlyBatches() {
        MDC.put("job", "payout-batching");
        MDC.put(RequestLoggingFilter.TRACE_ID, UUID.randomUUID().toString());

        long start = System.nanoTime();
        int batches = 0;
        long totalAmountByn = 0;

        try {
            OffsetDateTime periodStart = OffsetDateTime.now().minusMonths(1).withDayOfMonth(1);
            OffsetDateTime periodEnd = periodStart.plusMonths(1).minusDays(1);

            List<UUID> listInspectors = dsl.selectDistinct(INSPECTOR_PAYOUTS.INSPECTOR_ID)
                    .from(INSPECTOR_PAYOUTS)
                    .where(INSPECTOR_PAYOUTS.PAYOUT_BATCH_ID.isNull())
                    .fetch(INSPECTOR_PAYOUTS.INSPECTOR_ID);

            log.info("Payout batching started: periodStart={}, periodEnd={}, inspectors={}",
                    periodStart, periodEnd, listInspectors.size());

            for (UUID inspectorId : listInspectors) {

                var unbatched = dsl.selectFrom(INSPECTOR_PAYOUTS)
                        .where(INSPECTOR_PAYOUTS.INSPECTOR_ID.eq(inspectorId))
                        .and(INSPECTOR_PAYOUTS.PAYOUT_BATCH_ID.isNull())
                        .fetch();

                long amount = unbatched.stream().mapToLong(r -> r.get(INSPECTOR_PAYOUTS.AMOUNT_BYN)).sum();

                List<UUID> ids = unbatched.stream().map(r -> r.get(INSPECTOR_PAYOUTS.ID)).toList();

                UUID batchId = dsl.insertInto(PAYOUT_BATCHES)
                        .set(PAYOUT_BATCHES.INSPECTOR_ID, inspectorId)
                        .set(PAYOUT_BATCHES.PERIOD_START, periodStart)
                        .set(PAYOUT_BATCHES.PERIOD_END, periodEnd)
                        .set(PAYOUT_BATCHES.AMOUNT_BYN, amount)
                        .set(PAYOUT_BATCHES.STATUS, "pending")
                        .returning(PAYOUT_BATCHES.ID)
                        .fetchOne()
                        .getId();

                dsl.update(INSPECTOR_PAYOUTS)
                        .set(INSPECTOR_PAYOUTS.PAYOUT_BATCH_ID, batchId)
                        .where(INSPECTOR_PAYOUTS.ID.in(ids))
                        .execute();

                log.info("Payout batch created: batchId={}, inspectorId={}, payoutsCount={}, amountByn={}",
                        batchId, inspectorId, ids.size(), amount);
                batches++;
                totalAmountByn += amount;

                String inspectorEmail = dsl.select(INSPECTORS.EMAIL)
                        .from(INSPECTORS)
                        .where(INSPECTORS.ID.eq(inspectorId))
                        .fetchOne(INSPECTORS.EMAIL);

                if (inspectorEmail == null) {
                    log.warn("Inspector has no email, batch notification skipped: inspectorId={}, batchId={}",
                            inspectorId, batchId);
                } else {
                    notificationSender.notify(Channel.EMAIL, inspectorEmail,
                            "Поздравляем! Ваш выплата за " + periodStart.getMonth() + " месяца готова к оплате.");
                }
            }
        } catch (RuntimeException e) {
            log.error("Payout batching failed, run rolled back: durationMs={}", elapsedMs(start), e);
            throw e;
        } finally {
            log.info("Payout batching finished: batches={}, totalAmountByn={}, durationMs={}",
                    batches, totalAmountByn, elapsedMs(start));
            MDC.clear();
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}