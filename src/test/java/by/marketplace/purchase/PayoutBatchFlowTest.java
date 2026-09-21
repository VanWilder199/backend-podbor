package by.marketplace.purchase;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.TestNotificationSender;
import by.marketplace.car.enums.ReportStatus;
import by.marketplace.jooq.tables.records.PayoutBatchesRecord;
import by.marketplace.utils.InspectorUtils;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static by.marketplace.jooq.Tables.*;
import static by.marketplace.jooq.Tables.INSPECTORS;
import static org.assertj.core.api.Assertions.assertThat;

public class PayoutBatchFlowTest extends AbstractIntegrationTest {
    private final DSLContext dsl;
    private final PayoutBatchScheduler payoutBatchScheduler;
    private final TestNotificationSender testNotificationSender;

    @Autowired
    public PayoutBatchFlowTest(DSLContext dsl, PayoutBatchScheduler payoutBatchScheduler, TestNotificationSender testNotificationSender) {
        this.dsl = dsl;
        this.payoutBatchScheduler = payoutBatchScheduler;
        this.testNotificationSender = testNotificationSender;
    }

    @BeforeEach
    public void setUp() {
        dsl.truncate(INSPECTOR_PAYOUTS).cascade().execute();
        dsl.truncate(PAYOUT_BATCHES).cascade().execute();
        dsl.truncate(PURCHASES).cascade().execute();
        dsl.truncate(REPORTS).cascade().execute();
        dsl.truncate(CARS).cascade().execute();
        dsl.truncate(INSPECTORS).cascade().execute();
        dsl.truncate(USERS).cascade().execute();

        testNotificationSender.clearMessages();
    }

    @Test
    void schedulePayouts_shouldCreatePayoutBatches() {
        UUID inspector1 = insertInspector(InspectorUtils.USER_ID, "375291234567", "test1@test.com");
        UUID inspector2 = insertInspector(InspectorUtils.SECOND_USER_ID, "375291234569", "test9@test.com");

        insertPayout(inspector1, 100000L);
        insertPayout(inspector1, 50000L);
        insertPayout(inspector2, 50000L);

        payoutBatchScheduler.createMonthlyBatches();

        PayoutBatchesRecord records = dsl.selectFrom(PAYOUT_BATCHES)
                .where(PAYOUT_BATCHES.INSPECTOR_ID.in(inspector1))
                .fetchOne();


        assertThat(records.getAmountByn()).isEqualTo(150000L);
        assertThat(records.getStatus()).isEqualTo("pending");

        PayoutBatchesRecord records2 = dsl.selectFrom(PAYOUT_BATCHES)
                .where(PAYOUT_BATCHES.INSPECTOR_ID.in(inspector2))
                .fetchOne();


        assertThat(records2.getAmountByn()).isEqualTo(50000L);
        assertThat(records2.getStatus()).isEqualTo("pending");

        assertThat(testNotificationSender.getMessages()).hasSize(2);
    }

    private UUID insertInspector(long telegramUserId, String mobile, String email) {
        return dsl.insertInto(INSPECTORS)
                .set(INSPECTORS.TELEGRAM_USER_ID, telegramUserId)
                .set(INSPECTORS.FULL_NAME, InspectorUtils.FIRST_NAME)
                .set(INSPECTORS.PHONE, mobile)
                .set(INSPECTORS.EMAIL, email)
                .returning(INSPECTORS.ID)
                .fetchOne(INSPECTORS.ID);
    }

    private UUID insertPayout(UUID inspectorId, long amount) {
        UUID carId = dsl.insertInto(CARS)
                .set(CARS.MAKE, "Tesla")
                .set(CARS.MODEL, "Model S")
                .set(CARS.YEAR, 2022)
                .returning(CARS.ID)
                .fetchOne(CARS.ID);

        UUID reportId = dsl.insertInto(REPORTS)
                .set(REPORTS.CAR_ID, carId)
                .set(REPORTS.INSPECTOR_ID, inspectorId)
                .set(REPORTS.PRICE_BYN, amount)
                .set(REPORTS.STATUS, ReportStatus.PUBLISHED.getCode())
                .returning(REPORTS.ID)
                .fetchOne(REPORTS.ID);

        UUID buyerId = dsl.insertInto(USERS)
                .set(USERS.EMAIL, "buyer-" + UUID.randomUUID() + "@test.com")
                .returning(USERS.ID)
                .fetchOne(USERS.ID);

        UUID purchaseId = dsl.insertInto(PURCHASES)
                .set(PURCHASES.REPORT_ID, reportId)
                .set(PURCHASES.BUYER_ID, buyerId)
                .set(PURCHASES.AMOUNT_BYN, amount)
                .set(PURCHASES.STATUS, "paid")
                .returning(PURCHASES.ID)
                .fetchOne(PURCHASES.ID);

        return dsl.insertInto(INSPECTOR_PAYOUTS)
                .set(INSPECTOR_PAYOUTS.PURCHASE_ID, purchaseId)
                .set(INSPECTOR_PAYOUTS.INSPECTOR_ID, inspectorId)
                .set(INSPECTOR_PAYOUTS.AMOUNT_BYN, amount)
                .returning(INSPECTOR_PAYOUTS.ID)
                .fetchOne(INSPECTOR_PAYOUTS.ID);
    }
}
