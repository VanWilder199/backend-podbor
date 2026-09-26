package by.marketplace.admin;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.TestNotificationSender;
import by.marketplace.auth.service.JwtService;
import by.marketplace.inspector.dto.BanInspectorRequest;
import by.marketplace.inspector.dto.InspectorDto;
import by.marketplace.inspector.dto.RegisterCarReportRequest;
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

import java.util.UUID;

import static by.marketplace.jooq.Tables.*;
import static by.marketplace.jooq.Tables.ADMINS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты admin-эндпоинтов верификации/бана подборщиков (/admin/inspectors).
 */
public class AdminInspectorFlowTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "password";
    private static final String ADMIN_TOTP_SECRET = "secret";
    private static final String INSPECTOR_EMAIL = "test@test.com";

    private final TestRestTemplate restTemplate;
    private final DSLContext dsl;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TestNotificationSender notificationSender;

    @Autowired
    public AdminInspectorFlowTest(TestRestTemplate restTemplate,
                                  DSLContext dsl,
                                  JwtService jwtService,
                                  PasswordEncoder passwordEncoder,
                                  TestNotificationSender notificationSender) {
        this.restTemplate = restTemplate;
        this.dsl = dsl;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.notificationSender = notificationSender;
    }

    @BeforeEach
    void setUp() {
        dsl.truncate(REPORTS).cascade().execute();
        dsl.truncate(CARS).cascade().execute();
        dsl.truncate(INSPECTORS).cascade().execute();
        dsl.truncate(ADMINS).cascade().execute();
        dsl.truncate(REPORT_REQUESTS).cascade().execute();
        dsl.truncate(USERS).cascade().execute();

        dsl.insertInto(INSPECTORS)
                .set(INSPECTORS.TELEGRAM_USER_ID, InspectorUtils.USER_ID)
                .set(INSPECTORS.FULL_NAME, InspectorUtils.FIRST_NAME)
                .set(INSPECTORS.STATUS, "pending")
                .set(INSPECTORS.PHONE, "375291234567")
                .set(INSPECTORS.EMAIL, INSPECTOR_EMAIL)
                .execute();

        dsl.insertInto(ADMINS)
                .set(ADMINS.EMAIL, ADMIN_EMAIL)
                .set(ADMINS.PASSWORD_HASH, passwordEncoder.encode(ADMIN_PASSWORD))
                .set(ADMINS.TOTP_SECRET, ADMIN_TOTP_SECRET)
                .execute();

        notificationSender.getMessages().clear();
    }

    @Test
    void listInspectors_withStatusFilter_returnsOnlyPending() throws CodeGenerationException {
        dsl.insertInto(INSPECTORS)
                .set(INSPECTORS.TELEGRAM_USER_ID, 999L)
                .set(INSPECTORS.FULL_NAME, "Verified inspector")
                .set(INSPECTORS.STATUS, "verified")
                .set(INSPECTORS.PHONE, "375291111111")
                .set(INSPECTORS.EMAIL, "verified@test.com")
                .execute();

        ResponseEntity<InspectorDto[]> response = restTemplate.exchange(
                "/admin/inspectors?status=pending",
                HttpMethod.GET,
                new HttpEntity<>(null, adminHeaders()),
                InspectorDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].status()).isEqualTo("pending");
    }

    @Test
    void verifyInspector_onPending_returnsVerified() throws CodeGenerationException {
        UUID inspectorId = pendingInspectorId();

        ResponseEntity<InspectorDto> response = restTemplate.exchange(
                "/admin/inspectors/{id}/verify",
                HttpMethod.POST,
                new HttpEntity<>(null, adminHeaders()),
                InspectorDto.class,
                inspectorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("verified");
        assertThat(statusInDb(inspectorId)).isEqualTo("verified");
        assertThat(notificationSender.getMessages())
                .anyMatch(m -> m.contains(INSPECTOR_EMAIL) && m.contains("подтверждён"));
    }

    @Test
    void verifyInspector_secondVerify_returnsConflict() throws CodeGenerationException {
        UUID inspectorId = pendingInspectorId();

        ResponseEntity<InspectorDto> first = restTemplate.exchange(
                "/admin/inspectors/{id}/verify",
                HttpMethod.POST,
                new HttpEntity<>(null, adminHeaders()),
                InspectorDto.class,
                inspectorId);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ProblemDetail> second = restTemplate.exchange(
                "/admin/inspectors/{id}/verify",
                HttpMethod.POST,
                new HttpEntity<>(null, adminHeaders()),
                ProblemDetail.class,
                inspectorId);

        assertErrorCode(second, HttpStatus.CONFLICT, ErrorCode.INSPECTOR_NOT_PENDING);
    }

    @Test
    void banInspector_onPending_returnsBanned() throws CodeGenerationException {
        UUID inspectorId = pendingInspectorId();

        ResponseEntity<InspectorDto> response = restTemplate.exchange(
                "/admin/inspectors/{id}/ban",
                HttpMethod.POST,
                new HttpEntity<>(new BanInspectorRequest("Спам"), adminHeaders()),
                InspectorDto.class,
                inspectorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("banned");
        assertThat(statusInDb(inspectorId)).isEqualTo("banned");
        assertThat(notificationSender.getMessages())
                .anyMatch(m -> m.contains(INSPECTOR_EMAIL) && m.contains("Спам"));
    }

    @Test
    void banInspector_secondBan_returnsConflict() throws CodeGenerationException {
        UUID inspectorId = pendingInspectorId();

        ResponseEntity<InspectorDto> first = restTemplate.exchange(
                "/admin/inspectors/{id}/ban",
                HttpMethod.POST,
                new HttpEntity<>(new BanInspectorRequest("Спам"), adminHeaders()),
                InspectorDto.class,
                inspectorId);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ProblemDetail> second = restTemplate.exchange(
                "/admin/inspectors/{id}/ban",
                HttpMethod.POST,
                new HttpEntity<>(new BanInspectorRequest("Ещё раз"), adminHeaders()),
                ProblemDetail.class,
                inspectorId);

        assertErrorCode(second, HttpStatus.CONFLICT, ErrorCode.INSPECTOR_ALREADY_BANNED);
    }

    @Test
    void verifyInspector_nonExistent_returnsNotFound() throws CodeGenerationException {
        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/admin/inspectors/{id}/verify",
                HttpMethod.POST,
                new HttpEntity<>(null, adminHeaders()),
                ProblemDetail.class,
                UUID.randomUUID());

        assertErrorCode(response, HttpStatus.NOT_FOUND, ErrorCode.INSPECTOR_NOT_FOUND);
    }

    @Test
    void adminEndpoints_withBuyerJwt_returnsForbidden() throws Exception {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/admin/inspectors",
                HttpMethod.GET,
                new HttpEntity<>(null, buyerHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void bannedInspector_cannotCreateReport_profileStillVisible() throws Exception {
        UUID inspectorId = pendingInspectorId();
        dsl.update(INSPECTORS)
                .set(INSPECTORS.STATUS, "banned")
                .where(INSPECTORS.ID.eq(inspectorId))
                .execute();

        ResponseEntity<ProblemDetail> report = restTemplate.exchange(
                "/inspector/reports",
                HttpMethod.POST,
                new HttpEntity<>(
                        new RegisterCarReportRequest("https://www.av.by/auto/toyota/camry/12345678901234567"),
                        inspectorHeaders()),
                ProblemDetail.class);

        assertErrorCode(report, HttpStatus.FORBIDDEN, ErrorCode.INSPECTOR_BANNED);

        ResponseEntity<InspectorDto> profile = restTemplate.exchange(
                "/inspector/",
                HttpMethod.GET,
                new HttpEntity<>(inspectorHeaders()),
                InspectorDto.class);

        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody()).isNotNull();
        assertThat(profile.getBody().status()).isEqualTo("banned");
    }

    private HttpHeaders adminHeaders() throws CodeGenerationException {
        return AdminTestUtils.adminHeaders(restTemplate, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_TOTP_SECRET);
    }

    private HttpHeaders buyerHeaders() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "buyer@example.com", "BUYER");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders inspectorHeaders() throws Exception {
        String data = InspectorUtils.buildValidInitData(System.currentTimeMillis() / 1000, InspectorUtils.USER_ID);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Telegram-Data", data);
        return headers;
    }

    private UUID pendingInspectorId() {
        return dsl.select(INSPECTORS.ID).from(INSPECTORS)
                .where(INSPECTORS.STATUS.eq("pending"))
                .fetchOne(INSPECTORS.ID);
    }

    private String statusInDb(UUID inspectorId) {
        return dsl.select(INSPECTORS.STATUS).from(INSPECTORS)
                .where(INSPECTORS.ID.eq(inspectorId))
                .fetchOne(INSPECTORS.STATUS);
    }

    private void assertErrorCode(ResponseEntity<ProblemDetail> response, HttpStatus status, ErrorCode code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("errorCode")).isEqualTo(code.toString());
    }
}