package by.marketplace.purchase;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.TestNotificationSender;
import by.marketplace.car.enums.ReportStatus;
import by.marketplace.config.S3Properties;
import by.marketplace.purchase.bepaid.BePaidClient;
import by.marketplace.purchase.dto.ReportViewDto;
import by.marketplace.shared.exception.ErrorCode;
import by.marketplace.utils.InspectorUtils;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static by.marketplace.jooq.Tables.*;
import static by.marketplace.jooq.Tables.PURCHASES;
import static org.apache.commons.codec.digest.DigestUtils.sha256;
import static org.assertj.core.api.Assertions.assertThat;

public class ReportAccessFlowTest extends AbstractIntegrationTest {
    private final DSLContext dsl;
    private final TestNotificationSender testNotificationSender;
    private final TestRestTemplate restTemplate;
    private final S3Properties s3Properties;


    @MockitoBean
    private BePaidClient bePaidClient;

    @Autowired
    public ReportAccessFlowTest(DSLContext dsl, TestNotificationSender testNotificationSender, TestRestTemplate restTemplate, S3Properties s3Properties) {
        this.dsl = dsl;
        this.testNotificationSender = testNotificationSender;
        this.restTemplate = restTemplate;
        this.s3Properties = s3Properties;
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
    public void viewReport_byToken_returnsReportWithPresignedUrl() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId);
        String s3Key = "reports/" + reportId + "/photo1.jpg";
        insertReportMedia(reportId, s3Key);
        UUID buyerId =  insertBuyer("buyer@test.com");
        UUID purchaseId = insertPurchase(buyerId,reportId, "pending", "test", OffsetDateTime.now());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization","Basic dGVzdDp0ZXN0"); // test:test

        HttpEntity<String> body = new HttpEntity<>(
                "{\"transaction\":{\"uid\":\"unique-uid-1\",\"status\":\"successful\","
                     + "\"amount\":100000,\"currency\":\"BYN\",\"tracking_id\":\"" + purchaseId + "\"}}",
                     headers
        );

        restTemplate.exchange("/webhooks/bepaid", HttpMethod.POST, body, Void.class);

        String message = testNotificationSender.getMessages().getLast();
        assertThat(message).contains("/reports/view?token=");

        Matcher matcher = Pattern.compile("token=(\\S+)$").matcher(message);
        assertThat(matcher.find()).isTrue();
        String rawToken = matcher.group(1);

        ResponseEntity<ReportViewDto> response = restTemplate.exchange(
                "/reports/view?token={rawToken}", HttpMethod.GET,
                null,
                ReportViewDto.class,
                rawToken
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        ReportViewDto dto = response.getBody();
        assertThat(dto.id()).isEqualTo(reportId);
        assertThat(dto.carId()).isNotNull();

        assertThat(dto.globalMedia()).hasSize(1);
        String url = dto.globalMedia().get(0).url();

        assertThat(url).isNotEqualTo(s3Key);
        assertThat(url).startsWith(s3Properties.endpoint());
    }

    @Test
    public void viewReport_deletedAfterPurchase_returnsNotFound() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId);
        UUID buyerId =  insertBuyer("buyer@test.com");
        UUID purchaseId = insertPurchase(buyerId,reportId, "paid", "test", OffsetDateTime.now());

        String rawToken = "test-token";
        insertAccessToken(purchaseId, rawToken, OffsetDateTime.now().plusDays(30), null);

        ResponseEntity<ReportViewDto> before = restTemplate.exchange(
                "/reports/view?token={rawToken}",
                HttpMethod.GET,
                null,
                ReportViewDto.class,
                rawToken
        );

        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);

        dsl.update(REPORTS)
                .set(REPORTS.DELETED_AT, OffsetDateTime.now())
                .where(REPORTS.ID.eq(reportId))
                .execute();

        ResponseEntity<ProblemDetail> after = restTemplate.exchange(
                "/reports/view?token={rawToken}",
                HttpMethod.GET,
                null,
                ProblemDetail.class,
                rawToken
        );

        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(after.getBody().getProperties().get("errorCode"))
                .isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID.getCode());
    }

    @Test
    public void viewReport_expiredToken_returnsNotFound() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId);
        UUID buyerId =  insertBuyer("buyer@test.com");
        UUID purchaseId = insertPurchase(buyerId, reportId, "paid", "test", OffsetDateTime.now());

        String rawToken = "expired-token";
        insertAccessToken(purchaseId, rawToken, OffsetDateTime.now().minusDays(1), null);

        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/reports/view?token={rawToken}",
                HttpMethod.GET,
                null,
                ProblemDetail.class,
                rawToken
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("errorCode"))
                .isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID.getCode());
    }

    @Test
    public void viewReport_revokedToken_returnsNotFound() {
        UUID inspectorId = insertInspector();
        UUID reportId = insertReport(inspectorId);
        UUID buyerId =  insertBuyer("buyer@test.com");
        UUID purchaseId = insertPurchase(buyerId, reportId, "paid", "test", OffsetDateTime.now());

        String rawToken = "revoked-token";
        insertAccessToken(purchaseId, rawToken, OffsetDateTime.now().plusDays(30), OffsetDateTime.now());

        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/reports/view?token={rawToken}",
                HttpMethod.GET,
                null,
                ProblemDetail.class,
                rawToken
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("errorCode"))
                .isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID.getCode());
    }

    @Test
    public void viewReport_incorrectToken_returnsNotFound() {
        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/reports/view?token=incorrect-token",
                HttpMethod.GET,
                null,
                ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("errorCode"))
                .isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID.getCode());
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

  private void insertReportMedia(UUID reportId, String s3Key) {
            dsl.insertInto(REPORT_MEDIA)
                     .set(REPORT_MEDIA.REPORT_ID, reportId)
                     .set(REPORT_MEDIA.S3_KEY, s3Key)
                     .set(REPORT_MEDIA.KIND, "PHOTO")
                     .set(REPORT_MEDIA.STATUS, "ready")
                     .execute();
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

    private void insertAccessToken(UUID purchaseId, String rawToken, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
        dsl.insertInto(REPORT_ACCESS_TOKENS)
                .set(REPORT_ACCESS_TOKENS.PURCHASES_ID, purchaseId)
                .set(REPORT_ACCESS_TOKENS.TOKEN_HASH, HexFormat.of().formatHex(sha256(rawToken)))
                .set(REPORT_ACCESS_TOKENS.EXPIRES_AT, expiresAt)
                .set(REPORT_ACCESS_TOKENS.REVOKED_AT, revokedAt)
                .execute();
    }
}
