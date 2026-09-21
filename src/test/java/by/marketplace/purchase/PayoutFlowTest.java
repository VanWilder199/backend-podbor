package by.marketplace.purchase;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.jooq.tables.records.PayoutBatchesRecord;
import by.marketplace.purchase.service.PayoutService;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import by.marketplace.utils.InspectorUtils;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PayoutFlowTest extends AbstractIntegrationTest {

    private final DSLContext dsl;
    private final PayoutService payoutService;

    @Autowired
    public PayoutFlowTest(DSLContext dsl, PayoutService payoutService) {
        this.dsl = dsl;
        this.payoutService = payoutService;
    }

    @BeforeEach
    public void setUp() {
        dsl.truncate(INSPECTOR_PAYOUTS).cascade().execute();
        dsl.truncate(PAYOUT_BATCHES).cascade().execute();
        dsl.truncate(PURCHASES).cascade().execute();
        dsl.truncate(REPORTS).cascade().execute();
        dsl.truncate(CARS).cascade().execute();
        dsl.truncate(INSPECTORS).cascade().execute();
    }

    @Test
    void markAsPaid_pendingBatch_shouldMarkPaid() {
        UUID inspectorId = insertInspector();
        UUID batchId = insertPayoutBatch(inspectorId, "pending");

        payoutService.markAsPaid(batchId, UUID.randomUUID());

        PayoutBatchesRecord batch = dsl.selectFrom(PAYOUT_BATCHES)
                .where(PAYOUT_BATCHES.ID.eq(batchId))
                .fetchOne();

        assertThat(batch.getStatus()).isEqualTo("paid");
        assertThat(batch.getPaidAt()).isNotNull();
    }

    @Test
    void markAsPaid_missingBatch_shouldThrowNotFound() {
        insertInspector();

        assertThatThrownBy(() -> payoutService.markAsPaid(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYOUT_NOT_FOUND));
    }

    @Test
    void markAsPaid_alreadyPaidBatch_shouldThrowNotFound() {
        UUID inspectorId = insertInspector();
        UUID batchId = insertPayoutBatch(inspectorId, "paid");

        assertThatThrownBy(() -> payoutService.markAsPaid(batchId, UUID.randomUUID()))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYOUT_NOT_FOUND));
    }

    private UUID insertInspector() {
        return dsl.insertInto(INSPECTORS)
                .set(INSPECTORS.TELEGRAM_USER_ID, InspectorUtils.USER_ID)
                .set(INSPECTORS.FULL_NAME, InspectorUtils.FIRST_NAME)
                .set(INSPECTORS.PHONE, "375291234567")
                .set(INSPECTORS.EMAIL, "test@test.com")
                .returning(INSPECTORS.ID)
                .fetchOne(INSPECTORS.ID);
    }

    private UUID insertPayoutBatch(UUID inspectorId, String status) {
        return dsl.insertInto(PAYOUT_BATCHES)
                .set(PAYOUT_BATCHES.INSPECTOR_ID, inspectorId)
                .set(PAYOUT_BATCHES.PERIOD_START, OffsetDateTime.now().minusMonths(1))
                .set(PAYOUT_BATCHES.PERIOD_END, OffsetDateTime.now())
                .set(PAYOUT_BATCHES.AMOUNT_BYN, 80000L)
                .set(PAYOUT_BATCHES.STATUS, status)
                .returning(PAYOUT_BATCHES.ID)
                .fetchOne(PAYOUT_BATCHES.ID);
    }
}