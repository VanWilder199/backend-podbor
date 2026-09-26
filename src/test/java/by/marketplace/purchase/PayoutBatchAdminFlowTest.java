package by.marketplace.purchase;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.TestNotificationSender;
import by.marketplace.auth.service.JwtService;
import by.marketplace.car.enums.ReportStatus;
import by.marketplace.jooq.tables.records.PayoutBatchesRecord;
import by.marketplace.purchase.dto.PayoutBatch;
import by.marketplace.shared.exception.ErrorCode;
import by.marketplace.utils.AdminTestUtils;
import by.marketplace.utils.InspectorUtils;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.UUID;


import static by.marketplace.jooq.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;

public class PayoutBatchAdminFlowTest extends AbstractIntegrationTest {
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "password";
    private static final String ADMIN_TOTP_SECRET = "secret";


    private final DSLContext dsl;
    private final PayoutBatchScheduler payoutBatchScheduler;
    private final TestNotificationSender testNotificationSender;
    private final TestRestTemplate restTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Autowired
    public PayoutBatchAdminFlowTest(DSLContext dsl, PayoutBatchScheduler payoutBatchScheduler, TestNotificationSender testNotificationSender, TestRestTemplate restTemplate, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.dsl = dsl;
        this.payoutBatchScheduler = payoutBatchScheduler;
        this.testNotificationSender = testNotificationSender;
        this.restTemplate = restTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @BeforeEach
    public void setUp() {
        dsl.truncate(ADMINS).cascade().execute();
        dsl.truncate(INSPECTOR_PAYOUTS).cascade().execute();
        dsl.truncate(PAYOUT_BATCHES).cascade().execute();
        dsl.truncate(PURCHASES).cascade().execute();
        dsl.truncate(REPORTS).cascade().execute();
        dsl.truncate(CARS).cascade().execute();
        dsl.truncate(INSPECTORS).cascade().execute();
        dsl.truncate(USERS).cascade().execute();

        testNotificationSender.clearMessages();

        dsl.insertInto(ADMINS)
                .set(ADMINS.EMAIL, ADMIN_EMAIL)
                .set(ADMINS.PASSWORD_HASH, passwordEncoder.encode(ADMIN_PASSWORD))
                .set(ADMINS.TOTP_SECRET, ADMIN_TOTP_SECRET)
                .execute();
    }

    @Test
    public void  scheduler_aggregatesMultiplePayoutsPerInspector() {
        UUID inspectorId = insertInspector(InspectorUtils.USER_ID, "123456789", "test@example.com");
        UUID inspectorId2 = insertInspector(InspectorUtils.SECOND_USER_ID, "987654321", "test2@example.com");

        insertPayout(inspectorId, 100000);
        insertPayout(inspectorId, 200000);
        insertPayout(inspectorId2, 300000);
        insertPayout(inspectorId2, 400000);


        payoutBatchScheduler.createMonthlyBatches();

        PayoutBatchesRecord b1 = dsl.selectFrom(PAYOUT_BATCHES)
                .where(PAYOUT_BATCHES.INSPECTOR_ID.eq(inspectorId))
                .fetchOne();

        assertThat(b1.getAmountByn()).isEqualTo(300000L);
        assertThat(b1.getStatus()).isEqualTo("pending");

        PayoutBatchesRecord b2 = dsl.selectFrom(PAYOUT_BATCHES)
                .where(PAYOUT_BATCHES.INSPECTOR_ID.eq(inspectorId2))
                .fetchOne();

        assertThat(b2.getAmountByn()).isEqualTo(700000L);
        assertThat(b2.getStatus()).isEqualTo("pending");

        assertThat(testNotificationSender.getMessages()).hasSize(2);
    }

    @Test
    public void markAsPaid_onPending_thenSecondPay_returnsNotFound() throws CodeGenerationException {
        UUID inspectorId = insertInspector(InspectorUtils.USER_ID, "123456789", "test@example.com");


        insertPayout(inspectorId, 100000);

        payoutBatchScheduler.createMonthlyBatches();

        UUID batchId = dsl.select(PAYOUT_BATCHES.ID).from(PAYOUT_BATCHES)
                .where(PAYOUT_BATCHES.INSPECTOR_ID.eq(inspectorId))
                .fetchOne(PAYOUT_BATCHES.ID);

       HttpHeaders headers =  AdminTestUtils.adminHeaders(restTemplate, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_TOTP_SECRET);

        ResponseEntity<Void> response1  = restTemplate.exchange(
               "/admin/payouts/{id}/pay",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Void.class,
               batchId
       );

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);

        PayoutBatchesRecord b = dsl.selectFrom(PAYOUT_BATCHES)
                .where(PAYOUT_BATCHES.INSPECTOR_ID.eq(inspectorId))
                .fetchOne();


        assertThat(b.getStatus()).isEqualTo("paid");
        assertThat(b.getPaidAt()).isNotNull();


        ResponseEntity<ProblemDetail> response2  = restTemplate.exchange(
                "/admin/payouts/{id}/pay",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                ProblemDetail.class,
                batchId
        );


        assertErrorCode(response2, HttpStatus.NOT_FOUND, ErrorCode.PAYOUT_NOT_FOUND);
    }

    @Test
    public void listBatches_withStatusFilter_returnsOnlyMatching_withInspectorInfo() throws CodeGenerationException {
        UUID inspectorId = insertInspector(InspectorUtils.USER_ID, "123456789", "test@example.com");

        dsl.insertInto(PAYOUT_BATCHES)
                 .set(PAYOUT_BATCHES.INSPECTOR_ID, inspectorId)
                 .set(PAYOUT_BATCHES.PERIOD_START, OffsetDateTime.now().minusMonths(1))
                 .set(PAYOUT_BATCHES.PERIOD_END, OffsetDateTime.now())
                 .set(PAYOUT_BATCHES.AMOUNT_BYN, 50000L)
                 .set(PAYOUT_BATCHES.STATUS, "paid")
                 .set(PAYOUT_BATCHES.PAID_AT, OffsetDateTime.now())
                 .execute();

        insertPayout(inspectorId, 100000);
        insertPayout(inspectorId, 100000);

        payoutBatchScheduler.createMonthlyBatches();

        HttpHeaders headers =  AdminTestUtils.adminHeaders(restTemplate, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_TOTP_SECRET);
        ResponseEntity<PayoutBatch[]> response1  = restTemplate.exchange(
                "/admin/payouts?status=pending",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PayoutBatch[].class
        );

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody()).isNotNull();
        assertThat(response1.getBody()).hasSize(1);
        assertThat(response1.getBody()[0].status()).isEqualTo("pending");

        assertThat(response1.getBody()[0].inspectorEmail()).isEqualTo("test@example.com");
        assertThat(response1.getBody()[0].inspectorName()).isEqualTo(InspectorUtils.FIRST_NAME);
    }

    @Test
    public void listBatches_withoutFilter_returnsAll() throws CodeGenerationException {
        UUID inspector1 = insertInspector(InspectorUtils.USER_ID, "123456789", "test@example.com");
        UUID inspector2 = insertInspector(InspectorUtils.SECOND_USER_ID, "987654321", "test2@example.com");

        insertPayout(inspector1, 100000);
        insertPayout(inspector2, 200000);

        payoutBatchScheduler.createMonthlyBatches();

        HttpHeaders headers = AdminTestUtils.adminHeaders(restTemplate, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_TOTP_SECRET);
        ResponseEntity<PayoutBatch[]> response = restTemplate.exchange(
                "/admin/payouts",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PayoutBatch[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    public void markAsPaid_nonExistent_returnsNotFound() throws CodeGenerationException {
        HttpHeaders headers = AdminTestUtils.adminHeaders(restTemplate, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_TOTP_SECRET);

        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/admin/payouts/{id}/pay",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                ProblemDetail.class,
                UUID.randomUUID());

        assertErrorCode(response, HttpStatus.NOT_FOUND, ErrorCode.PAYOUT_NOT_FOUND);
    }

    @Test
    public void adminEndpoints_withBuyerJwt_returnsForbidden() throws Exception {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/admin/payouts",
                HttpMethod.GET,
                new HttpEntity<>(buyerHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders buyerHeaders() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "buyer@example.com", "BUYER");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private void assertErrorCode(ResponseEntity<ProblemDetail> response, HttpStatus status, ErrorCode code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("errorCode")).isEqualTo(code.toString());
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
