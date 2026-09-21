package by.marketplace.purchase;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.TestNotificationSender;
import by.marketplace.auth.service.JwtService;
import by.marketplace.car.enums.ReportStatus;
import by.marketplace.jooq.tables.records.PurchasesRecord;
import by.marketplace.purchase.bepaid.BePaidCheckoutResponse;
import by.marketplace.purchase.bepaid.BePaidClient;
import by.marketplace.purchase.dto.InitiatePurchaseRequest;
import by.marketplace.purchase.dto.InitiatePurchaseResponse;
import by.marketplace.purchase.dto.PurchaseDto;
import by.marketplace.purchase.service.PayoutService;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import by.marketplace.utils.InspectorUtils;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;
import static by.marketplace.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;

public class PurchaseFlowTest  extends AbstractIntegrationTest {
    private final TestRestTemplate restTemplate;
    private final DSLContext dsl;
    private final JwtService jwtService;
    private final TestNotificationSender testNotificationSender;
    private final BePaidReconcileService reconcileService;
    private final PayoutBatchScheduler payoutBatchScheduler;
    private final PayoutService payoutService;

    @MockitoBean
    private BePaidClient bePaidClient;

    @Autowired
    public PurchaseFlowTest(
            TestRestTemplate restTemplate,
            DSLContext dsl,
            JwtService jwtService,
            TestNotificationSender testNotificationSender,
            BePaidReconcileService reconcileService,
            PayoutBatchScheduler payoutBatchScheduler,
            PayoutService payoutService
    ) {
        this.restTemplate = restTemplate;
        this.dsl = dsl;
        this.jwtService = jwtService;
        this.testNotificationSender = testNotificationSender;
        this.reconcileService = reconcileService;
        this.payoutBatchScheduler = payoutBatchScheduler;
        this.payoutService = payoutService;
    }

    @BeforeEach
    public void setUp() {
        Mockito.when(bePaidClient.createCheckout(Mockito.any(), Mockito.anyLong(), Mockito.any()))
                        .thenReturn(new BePaidCheckoutResponse(new BePaidCheckoutResponse.Checkout("fake-token")));

        dsl.truncate(PURCHASES).cascade().execute();
        dsl.truncate(IDEMPOTENCY_KEYS).cascade().execute();
        dsl.truncate(REPORT_ACCESS_TOKENS).cascade().execute();
        dsl.truncate(PAYOUT_BATCHES).cascade().execute();
        dsl.truncate(INSPECTOR_PAYOUTS).cascade().execute();
        dsl.truncate(REPORTS).cascade().execute();
        dsl.truncate(CARS).cascade().execute();
        dsl.truncate(INSPECTORS).cascade().execute();
        dsl.truncate(ADMINS).cascade().execute();
        dsl.truncate(REPORT_REQUESTS).cascade().execute();
        dsl.truncate(USERS).cascade().execute();
    }

    @Test
    void initiatePurchase_reportNotPublshed_shouldReturnConflict() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId, 100000, ReportStatus.DRAFT.getCode());
        UUID buyerId = insertBuyer("buyer@example.com");


        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/purchases", HttpMethod.POST,
                new HttpEntity<>(new InitiatePurchaseRequest(reportId), buyerHeaders(buyerId)),
                ProblemDetail.class
        );


        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties().get("errorCode"))
                .isEqualTo(ErrorCode.REPORT_NOT_PUBLISHED.getCode());
    }

    @Test
    void initiatePurchase_reportNotPublshed_shouldSuccess() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID buyerId = insertBuyer("buyer@example.com");


        ResponseEntity<InitiatePurchaseResponse> response = restTemplate.exchange(
                "/purchases", HttpMethod.POST,
                new HttpEntity<>(new InitiatePurchaseRequest(reportId), buyerHeaders(buyerId)),
                InitiatePurchaseResponse.class
        );


        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().redirectUrl())
                .endsWith("fake-token");
        assertThat(response.getBody().purchaseId())
                .isNotNull();
    }

    @Test
    void handleWebhook_successful_shouldMarkPaidCreatePayoutAndToken() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID buyerId = insertBuyer("buyer@example.com");
        UUID purchaseId = insertPurchase(reportId, buyerId,"pending", "fake-token");

        HttpHeaders headers = new HttpHeaders();

        headers.set("Authorization", "Basic dGVzdDp0ZXN0"); // Base64(test:test) =dGVzdDp0ZXN0,

        HttpEntity<String> body = new HttpEntity<>(
        "{\"transaction\":{\"uid\":\"unique-uid-1\",\"status\":\"successful\","
                     + "\"amount\":100000,\"currency\":\"BYN\",\"tracking_id\":\"" + purchaseId + "\"}}",
                             headers

        );

        ResponseEntity<Void> response = restTemplate.exchange(
                "/webhooks/bepaid",
                HttpMethod.POST,
                body,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        PurchasesRecord purchasesRecord = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.ID.eq(purchaseId))
                .fetchOne();

        assertThat(purchasesRecord.getStatus()).isEqualTo("paid");
        assertThat(purchasesRecord.getPaidAt()).isNotNull();

        Long payout = dsl.select(INSPECTOR_PAYOUTS.AMOUNT_BYN)
                        .from(INSPECTOR_PAYOUTS)
                                .where(INSPECTOR_PAYOUTS.PURCHASE_ID.eq(purchaseId))
                                        .fetchOne(INSPECTOR_PAYOUTS.AMOUNT_BYN);

        assertThat(payout).isEqualTo(80000L);

        Integer token = dsl.fetchCount(
                dsl.selectFrom(REPORT_ACCESS_TOKENS).where(REPORT_ACCESS_TOKENS.PURCHASES_ID.eq(purchaseId))
        );

        assertThat(token).isEqualTo(1);

        assertThat(testNotificationSender.getMessages()).isNotEmpty();
    }

    @Test
    void handleWebhook_successful_shouldNotCreateDoublePayoutAndToken() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID buyerId = insertBuyer("buyer@example.com");
        UUID purchaseId = insertPurchase(reportId, buyerId,"pending", "fake-token");

        HttpHeaders headers = new HttpHeaders();

        headers.set("Authorization", "Basic dGVzdDp0ZXN0"); // Base64(test:test) =dGVzdDp0ZXN0,

        HttpEntity<String> body = new HttpEntity<>(
                "{\"transaction\":{\"uid\":\"unique-uid-1\",\"status\":\"successful\","
                        + "\"amount\":100000,\"currency\":\"BYN\",\"tracking_id\":\"" + purchaseId + "\"}}",
                headers

        );

        ResponseEntity<Void> response = restTemplate.exchange(
                "/webhooks/bepaid",
                HttpMethod.POST,
                body,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        PurchasesRecord purchasesRecord = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.ID.eq(purchaseId))
                .fetchOne();

        assertThat(purchasesRecord.getStatus()).isEqualTo("paid");
        assertThat(purchasesRecord.getPaidAt()).isNotNull();

        ResponseEntity<Void> response2 = restTemplate.exchange(
                "/webhooks/bepaid",
                HttpMethod.POST,
                body,
                Void.class
        );

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);


        Integer token2 = dsl.fetchCount(
                dsl.selectFrom(REPORT_ACCESS_TOKENS).where(REPORT_ACCESS_TOKENS.PURCHASES_ID.eq(purchaseId))
        );

        assertThat(token2).isEqualTo(1);

         int payouts = dsl.fetchCount(
                        dsl.selectFrom(INSPECTOR_PAYOUTS)
                            .where(INSPECTOR_PAYOUTS.PURCHASE_ID.eq(purchaseId)));

         assertThat(payouts).isEqualTo(1);

    }

    @Test
    void getPurchases_shouldReturnBuyersOwnPurchases() {
        UUID inspectorId = insertInspector();
        UUID report1Id = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID report2Id = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID report3Id = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID buyer1 = insertBuyer("buyer1@example.com");
        UUID buyer2 = insertBuyer("buyer2@example.com");

        UUID buyer1Purchase1 = insertPurchase(report1Id, buyer1, "paid", "token1");
        UUID buyer1Purchase2 = insertPurchase(report2Id, buyer1, "pending", "token2");
        UUID buyer2Purchase = insertPurchase(report3Id, buyer2, "pending", "token3");

        ResponseEntity<List<PurchaseDto>> response = restTemplate.exchange(
                "/purchases", HttpMethod.GET,
                new HttpEntity<>(buyerHeaders(buyer1)),
                new ParameterizedTypeReference<List<PurchaseDto>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);

        List<UUID> ids = response.getBody().stream().map(PurchaseDto::id).toList();
        assertThat(ids).containsExactlyInAnyOrder(buyer1Purchase1, buyer1Purchase2);
        assertThat(ids).doesNotContain(buyer2Purchase);
    }

    @Test
    void handleWebhook_badAuth_shouldReturnInvalidSignature() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID buyerId = insertBuyer("buyer@example.com");
        UUID purchaseId = insertPurchase(reportId, buyerId, "pending", "fake-token");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic d3Jvbmc="); // wrong auth -> INVALID_WEBHOOK_SIGNATURE

        HttpEntity<String> body = new HttpEntity<>(
                "{\"transaction\":{\"uid\":\"unique-uid-b6\",\"status\":\"successful\","
                        + "\"amount\":100000,\"currency\":\"BYN\",\"tracking_id\":\"" + purchaseId + "\"}}",
                headers
        );

        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/webhooks/bepaid",
                HttpMethod.POST,
                body,
                ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("errorCode"))
                .isEqualTo(ErrorCode.INVALID_WEBHOOK_SIGNATURE.getCode());

        PurchasesRecord purchasesRecord = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.ID.eq(purchaseId))
                .fetchOne();
        assertThat(purchasesRecord.getStatus()).isEqualTo("pending");
    }

    @Test
    void handleWebhook_failed_shouldMarkPurchaseFailed() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID buyerId = insertBuyer("buyer@example.com");
        UUID purchaseId = insertPurchase(reportId, buyerId, "pending", "fake-token");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic dGVzdDp0ZXN0");

        HttpEntity<String> body = new HttpEntity<>(
                "{\"transaction\":{\"uid\":\"unique-uid-b9\",\"status\":\"failed\","
                        + "\"amount\":100000,\"currency\":\"BYN\",\"tracking_id\":\"" + purchaseId + "\"}}",
                headers
        );

        ResponseEntity<Void> response = restTemplate.exchange(
                "/webhooks/bepaid",
                HttpMethod.POST,
                body,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        PurchasesRecord purchasesRecord = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.ID.eq(purchaseId))
                .fetchOne();
        assertThat(purchasesRecord.getStatus()).isEqualTo("failed");

        int payouts = dsl.fetchCount(
                dsl.selectFrom(INSPECTOR_PAYOUTS).where(INSPECTOR_PAYOUTS.PURCHASE_ID.eq(purchaseId)));
        assertThat(payouts).isZero();

        int tokens = dsl.fetchCount(
                dsl.selectFrom(REPORT_ACCESS_TOKENS).where(REPORT_ACCESS_TOKENS.PURCHASES_ID.eq(purchaseId)));
        assertThat(tokens).isZero();
    }

    @Test
    void handleWebhook_unknownTrackingId_shouldIgnore() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic dGVzdDp0ZXN0");

        // (a) tracking_id is a non-UUID garbage string -> ignored with HTTP 200
        HttpEntity<String> bodyA = new HttpEntity<>(
                "{\"transaction\":{\"uid\":\"unique-uid-b10a\",\"status\":\"successful\","
                        + "\"amount\":100000,\"currency\":\"BYN\",\"tracking_id\":\"not-a-uuid\"}}",
                headers
        );

        ResponseEntity<Void> responseA = restTemplate.exchange(
                "/webhooks/bepaid",
                HttpMethod.POST,
                bodyA,
                Void.class
        );
        assertThat(responseA.getStatusCode()).isEqualTo(HttpStatus.OK);

        // (b) tracking_id is a valid but non-existent UUID -> ignored, canary purchase stays pending
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID buyerId = insertBuyer("buyer@example.com");
        UUID purchaseId = insertPurchase(reportId, buyerId, "pending", "fake-token");

        HttpEntity<String> bodyB = new HttpEntity<>(
                "{\"transaction\":{\"uid\":\"unique-uid-b10b\",\"status\":\"successful\","
                        + "\"amount\":100000,\"currency\":\"BYN\",\"tracking_id\":\"" + UUID.randomUUID() + "\"}}",
                headers
        );

        ResponseEntity<Void> responseB = restTemplate.exchange(
                "/webhooks/bepaid",
                HttpMethod.POST,
                bodyB,
                Void.class
        );
        assertThat(responseB.getStatusCode()).isEqualTo(HttpStatus.OK);

        PurchasesRecord purchasesRecord = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.ID.eq(purchaseId))
                .fetchOne();
        assertThat(purchasesRecord.getStatus()).isEqualTo("pending");
    }

    @Test
    void handleWebhook_unknownStatus_shouldIgnore() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID buyerId = insertBuyer("buyer@example.com");
        UUID purchaseId = insertPurchase(reportId, buyerId, "pending", "fake-token");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic dGVzdDp0ZXN0");

        HttpEntity<String> body = new HttpEntity<>(
                "{\"transaction\":{\"uid\":\"unique-uid-b11\",\"status\":\"processing\","
                        + "\"amount\":100000,\"currency\":\"BYN\",\"tracking_id\":\"" + purchaseId + "\"}}",
                headers
        );

        ResponseEntity<Void> response = restTemplate.exchange(
                "/webhooks/bepaid",
                HttpMethod.POST,
                body,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        PurchasesRecord purchasesRecord = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.ID.eq(purchaseId))
                .fetchOne();
        assertThat(purchasesRecord.getStatus()).isEqualTo("pending");

        int payouts = dsl.fetchCount(
                dsl.selectFrom(INSPECTOR_PAYOUTS).where(INSPECTOR_PAYOUTS.PURCHASE_ID.eq(purchaseId)));
        assertThat(payouts).isZero();

        int tokens = dsl.fetchCount(
                dsl.selectFrom(REPORT_ACCESS_TOKENS).where(REPORT_ACCESS_TOKENS.PURCHASES_ID.eq(purchaseId)));
        assertThat(tokens).isZero();
    }

    @Test
    void initiatePurchase_duplicateActivePurchase_shouldConflict() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID buyerId = insertBuyer("buyer@example.com");


        ResponseEntity<InitiatePurchaseResponse> response = restTemplate.exchange(
                "/purchases", HttpMethod.POST,
                new HttpEntity<>(new InitiatePurchaseRequest(reportId), buyerHeaders(buyerId)),
                InitiatePurchaseResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ProblemDetail> response2 = restTemplate.exchange(
                "/purchases", HttpMethod.POST,
                new HttpEntity<>(new InitiatePurchaseRequest(reportId), buyerHeaders(buyerId)),
                ProblemDetail.class
        );

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response2.getBody().getProperties().get("errorCode"))
                .isEqualTo(ErrorCode.PURCHASE_ALREADY_EXISTS.getCode());
    }

    @Test
    void initiatePurchase_bePaidFails_shouldReturnBadGatewayAndMarkFailed() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId, 100000, ReportStatus.PUBLISHED.getCode());
        UUID buyerId = insertBuyer("buyer@example.com");
        Mockito.when(bePaidClient.createCheckout(Mockito.any(), Mockito.anyLong(), Mockito.any()))
                .thenThrow(new AppException(new AppException(ErrorCode.BEPAID_REQUEST_FAILED).getErrorCode()));



        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/purchases", HttpMethod.POST,
                new HttpEntity<>(new InitiatePurchaseRequest(reportId), buyerHeaders(buyerId)),
                ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getProperties().get("errorCode"))
                  .isEqualTo(ErrorCode.BEPAID_REQUEST_FAILED.getCode());

        PurchasesRecord purchasesRecord = dsl.selectFrom(PURCHASES)
                .where(PURCHASES.BUYER_ID.eq(buyerId))
                .and(PURCHASES.REPORT_ID.eq(reportId))
                .fetchOne();

        assertThat(purchasesRecord.getStatus()).isEqualTo("failed");
        assertThat(purchasesRecord.getBepaidCheckoutToken()).isNull();
    }


    private UUID insertInspector() {
        return  dsl.insertInto(INSPECTORS)
                .set(INSPECTORS.TELEGRAM_USER_ID, InspectorUtils.USER_ID)
                .set(INSPECTORS.FULL_NAME, InspectorUtils.FIRST_NAME)
                .set(INSPECTORS.PHONE, "375291234567")
                .set(INSPECTORS.EMAIL, "test@test.com")
                .returning(INSPECTORS.ID)
                .fetchOne(INSPECTORS.ID);
    }

    private UUID insertReport(UUID inspectorId, long priceByn, String status) {
        UUID carId = dsl.insertInto(CARS)
                .set(CARS.MAKE, "Tesla")
                .set(CARS.MODEL, "Model S")
                .set(CARS.YEAR, 2022)
                .returning(CARS.ID)
                .fetchOne(CARS.ID);


        return dsl.insertInto(REPORTS)
                .set(REPORTS.CAR_ID, carId)
                .set(REPORTS.INSPECTOR_ID, inspectorId)
                .set(REPORTS.PRICE_BYN, priceByn)
                .set(REPORTS.STATUS, status)
                .returning(REPORTS.ID)
                .fetchOne(REPORTS.ID);
    }

    private UUID insertBuyer(String email) {
        return dsl.insertInto(USERS)
                .set(USERS.EMAIL, email)
                .returning(USERS.ID)
                .fetchOne(USERS.ID);
    }

    private UUID insertPurchase(UUID reportId, UUID buyerId, String status, String token) {
        return dsl.insertInto(PURCHASES)
                .set(PURCHASES.REPORT_ID, reportId)
                .set(PURCHASES.BUYER_ID, buyerId)
                .set(PURCHASES.STATUS, status)
                .set(PURCHASES.AMOUNT_BYN, 100000L)
                .set(PURCHASES.BEPAID_CHECKOUT_TOKEN, token)
                .returning(PURCHASES.ID)
                .fetchOne(PURCHASES.ID);
    }

    private HttpHeaders buyerHeaders(UUID buyerId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.generateAccessToken(buyerId, "buyer@example.com", "BUYER"));
        return headers;
    }
}
