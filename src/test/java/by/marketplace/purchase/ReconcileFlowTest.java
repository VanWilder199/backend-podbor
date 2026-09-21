package by.marketplace.purchase;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.TestNotificationSender;
import by.marketplace.car.enums.ReportStatus;
import by.marketplace.jooq.tables.records.PurchasesRecord;
import by.marketplace.purchase.bepaid.BePaidCheckoutStatusResponse;
import by.marketplace.purchase.bepaid.BePaidClient;
import by.marketplace.utils.InspectorUtils;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;

public class ReconcileFlowTest extends AbstractIntegrationTest {

    private final DSLContext dsl;
    private final BePaidReconcileService reconcileService;
    private final TestNotificationSender testNotificationSender;

    @MockitoBean
    private BePaidClient bePaidClient;

    @Autowired
    public ReconcileFlowTest(
            DSLContext dsl,
            BePaidReconcileService reconcileService,
            TestNotificationSender testNotificationSender
    ) {
        this.dsl = dsl;
        this.reconcileService = reconcileService;
        this.testNotificationSender = testNotificationSender;
    }

    @BeforeEach
    public void setUp() {
        dsl.truncate(INSPECTOR_PAYOUTS).cascade().execute();
        dsl.truncate(PAYOUT_BATCHES).cascade().execute();
        dsl.truncate(REPORT_ACCESS_TOKENS).cascade().execute();
        dsl.truncate(IDEMPOTENCY_KEYS).cascade().execute();
        dsl.truncate(PURCHASES).cascade().execute();
        dsl.truncate(REPORTS).cascade().execute();
        dsl.truncate(CARS).cascade().execute();
        dsl.truncate(INSPECTORS).cascade().execute();
        dsl.truncate(USERS).cascade().execute();

        testNotificationSender.clearMessages();
    }

    @Test
    void reconcile_successful_shouldMarkPaidCreatePayoutAndToken() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId);
        UUID buyerId = insertBuyer("buyer1@example.com");
        UUID purchaseId = insertPurchase(buyerId, reportId, "pending", "token-1",
                OffsetDateTime.now().minusHours(2));

        Mockito.when(bePaidClient.getCheckoutStatus(Mockito.any()))
                .thenReturn(successfulResponse("token-1", "uid-1"));

        reconcileService.reconcile();

        PurchasesRecord purchase = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.ID.eq(purchaseId))
                .fetchOne();

        assertThat(purchase.getStatus()).isEqualTo("paid");
        assertThat(purchase.getPaidAt()).isNotNull();

        Long payout = dsl.select(INSPECTOR_PAYOUTS.AMOUNT_BYN)
                .from(INSPECTOR_PAYOUTS)
                .where(INSPECTOR_PAYOUTS.PURCHASE_ID.eq(purchaseId))
                .fetchOne(INSPECTOR_PAYOUTS.AMOUNT_BYN);

        assertThat(payout).isEqualTo(80000L);

        Integer tokenCount = dsl.fetchCount(
                dsl.selectFrom(REPORT_ACCESS_TOKENS)
                        .where(REPORT_ACCESS_TOKENS.PURCHASES_ID.eq(purchaseId))
        );

        assertThat(tokenCount).isEqualTo(1);

        assertThat(testNotificationSender.getMessages()).isNotEmpty();
    }

    @Test
    void reconcile_failed_shouldMarkPurchaseFailed() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId);
        UUID buyerId = insertBuyer("buyer2@example.com");
        UUID purchaseId = insertPurchase(buyerId, reportId, "pending", "token-2",
                OffsetDateTime.now().minusHours(2));

        Mockito.when(bePaidClient.getCheckoutStatus(Mockito.any()))
                .thenReturn(statusResponse("token-2", "uid-2", "failed"));

        reconcileService.reconcile();

        PurchasesRecord purchase = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.ID.eq(purchaseId))
                .fetchOne();

        assertThat(purchase.getStatus()).isEqualTo("failed");

        int payouts = dsl.fetchCount(
                dsl.selectFrom(INSPECTOR_PAYOUTS)
                        .where(INSPECTOR_PAYOUTS.PURCHASE_ID.eq(purchaseId))
        );

        assertThat(payouts).isZero();

        int tokenCount = dsl.fetchCount(
                dsl.selectFrom(REPORT_ACCESS_TOKENS)
                        .where(REPORT_ACCESS_TOKENS.PURCHASES_ID.eq(purchaseId))
        );

        assertThat(tokenCount).isZero();
    }

    @Test
    void reconcile_youngPurchase_shouldBeSkipped() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId);
        UUID buyerId = insertBuyer("buyer3@example.com");
        UUID purchaseId = insertPurchase(buyerId, reportId, "pending", "token-3",
                OffsetDateTime.now());

        reconcileService.reconcile();

        Mockito.verify(bePaidClient, never()).getCheckoutStatus(Mockito.any());

        PurchasesRecord purchase = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.ID.eq(purchaseId))
                .fetchOne();

        assertThat(purchase.getStatus()).isEqualTo("pending");
    }

    @Test
    void reconcile_duplicateUid_shouldNotDoublePay() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId);
        UUID buyerId = insertBuyer("buyer4@example.com");
        UUID purchaseId = insertPurchase(buyerId, reportId, "pending", "token-4",
                OffsetDateTime.now().minusHours(2));

        dsl.insertInto(IDEMPOTENCY_KEYS)
                .set(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY, "uid-1")
                .execute();

        Mockito.when(bePaidClient.getCheckoutStatus(Mockito.any()))
                .thenReturn(successfulResponse("token-4", "uid-1"));

        reconcileService.reconcile();

        PurchasesRecord purchase = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.ID.eq(purchaseId))
                .fetchOne();

        assertThat(purchase.getStatus()).isEqualTo("pending");

        int payouts = dsl.fetchCount(
                dsl.selectFrom(INSPECTOR_PAYOUTS)
                        .where(INSPECTOR_PAYOUTS.PURCHASE_ID.eq(purchaseId))
        );

        assertThat(payouts).isZero();

        int tokenCount = dsl.fetchCount(
                dsl.selectFrom(REPORT_ACCESS_TOKENS)
                        .where(REPORT_ACCESS_TOKENS.PURCHASES_ID.eq(purchaseId))
        );

        assertThat(tokenCount).isZero();
    }

    private BePaidCheckoutStatusResponse successfulResponse(String token, String uid) {
        return statusResponse(token, uid, "successful");
    }

    private BePaidCheckoutStatusResponse statusResponse(String token, String uid, String status) {
        return new BePaidCheckoutStatusResponse(
                new BePaidCheckoutStatusResponse.Checkout(token,
                        new BePaidCheckoutStatusResponse.GatewayResponse(
                                new BePaidCheckoutStatusResponse.Payment(uid, status, 100000L, "BYN"))));
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

    private UUID insertReport(UUID inspectorId) {
        UUID carId = dsl.insertInto(CARS)
                .set(CARS.MAKE, "Tesla")
                .set(CARS.MODEL, "Model S")
                .set(CARS.YEAR, 2022)
                .returning(CARS.ID)
                .fetchOne(CARS.ID);

        return dsl.insertInto(REPORTS)
                .set(REPORTS.CAR_ID, carId)
                .set(REPORTS.INSPECTOR_ID, inspectorId)
                .set(REPORTS.PRICE_BYN, 100000L)
                .set(REPORTS.STATUS, ReportStatus.PUBLISHED.getCode())
                .returning(REPORTS.ID)
                .fetchOne(REPORTS.ID);
    }

    private UUID insertBuyer(String email) {
        return dsl.insertInto(USERS)
                .set(USERS.EMAIL, email)
                .returning(USERS.ID)
                .fetchOne(USERS.ID);
    }

    private UUID insertPurchase(UUID buyerId, UUID reportId, String status, String token, OffsetDateTime createdAt) {
        return dsl.insertInto(PURCHASES)
                .set(PURCHASES.REPORT_ID, reportId)
                .set(PURCHASES.BUYER_ID, buyerId)
                .set(PURCHASES.STATUS, status)
                .set(PURCHASES.AMOUNT_BYN, 100000L)
                .set(PURCHASES.BEPAID_CHECKOUT_TOKEN, token)
                .set(PURCHASES.CREATED_AT, createdAt)
                .returning(PURCHASES.ID)
                .fetchOne(PURCHASES.ID);
    }
}