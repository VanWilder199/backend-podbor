package by.marketplace.purchase;

import by.marketplace.auth.dto.Channel;
import by.marketplace.notification.NotificationSender;
import org.jooq.DSLContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;

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
        OffsetDateTime periodStart = OffsetDateTime.now().minusMonths(1).withDayOfMonth(1);
        OffsetDateTime periodEnd = periodStart.plusMonths(1).minusDays(1);

        List<UUID> listInspectors = dsl.selectDistinct(INSPECTOR_PAYOUTS.INSPECTOR_ID)
                .from(INSPECTOR_PAYOUTS)
                .where(INSPECTOR_PAYOUTS.PAYOUT_BATCH_ID.isNull())
                .fetch(INSPECTOR_PAYOUTS.INSPECTOR_ID);


        for (UUID inspectorId : listInspectors) {

            var unbatched = dsl.select(INSPECTOR_PAYOUTS)
                    .where(INSPECTOR_PAYOUTS.INSPECTOR_ID.eq(inspectorId))
                    .and(INSPECTOR_PAYOUTS.PAYOUT_BATCH_ID.isNull())
                    .fetch();

            long amount = unbatched.stream().mapToLong(r -> r.get(INSPECTOR_PAYOUTS.AMOUNT_BYN)).sum();

            List<UUID> ids = unbatched.stream().map(r -> r.get(INSPECTOR_PAYOUTS.ID)).toList();


           UUID batchId =  dsl.insertInto(PAYOUT_BATCHES)
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

            String inspectorEmail = dsl.select(INSPECTORS.EMAIL)
                    .from(INSPECTORS)
                    .where(INSPECTORS.ID.eq(inspectorId))
                    .fetchOne(INSPECTORS.EMAIL);

            notificationSender.notify(Channel.EMAIL, inspectorEmail, "Поздравляем! Ваш выплата за " + periodStart.getMonth() + " месяца готова к оплате.");
        }


    }

}
