package by.marketplace.purchase;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.TestNotificationSender;
import by.marketplace.auth.service.JwtService;
import by.marketplace.car.enums.ReportStatus;
import by.marketplace.purchase.bepaid.BePaidCheckoutResponse;
import by.marketplace.purchase.bepaid.BePaidClient;
import by.marketplace.purchase.dto.InitiatePurchaseRequest;
import by.marketplace.purchase.dto.InitiatePurchaseResponse;
import by.marketplace.purchase.service.PayoutService;
import by.marketplace.shared.exception.ErrorCode;
import by.marketplace.utils.InspectorUtils;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
        Mockito.when(bePaidClient.createCheckout(Mockito.any(), Mockito.any(), Mockito.any()))
                        .thenReturn(new BePaidCheckoutResponse(new BePaidCheckoutResponse.Checkout("fake-token")));

        dsl.truncate(PURCHASES).cascade().execute();
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

    private HttpHeaders buyerHeaders(UUID buyerId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.generateAccessToken(buyerId, "buyer@example.com", "BUYER"));
        return headers;
    }
}
