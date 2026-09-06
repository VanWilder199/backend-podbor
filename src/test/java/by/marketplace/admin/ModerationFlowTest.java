package by.marketplace.admin;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.TestNotificationSender;
import by.marketplace.admin.dto.AdminReportDetailDto;
import by.marketplace.admin.dto.DeleteReportRequest;
import by.marketplace.admin.dto.ModerationQueueItemDto;
import by.marketplace.admin.dto.ReviseReportRequest;
import by.marketplace.shared.exception.ErrorCode;
import by.marketplace.auth.dto.AdminAuthResponse;
import by.marketplace.auth.dto.AdminLoginRequest;
import by.marketplace.auth.service.JwtService;
import by.marketplace.car.AvByParser;
import by.marketplace.car.dto.CarParseData;
import by.marketplace.car.dto.SectionItemInput;
import by.marketplace.car.dto.UpdateConclusionRequest;
import by.marketplace.car.dto.UpdateSectionRequest;
import by.marketplace.car.enums.ItemStatus;
import by.marketplace.car.enums.ReportStatus;
import by.marketplace.inspector.dto.ConfirmUploadRequest;
import by.marketplace.inspector.dto.CreateReportResponse;
import by.marketplace.inspector.dto.MediaKind;
import by.marketplace.inspector.dto.PresignedUrlRequest;
import by.marketplace.inspector.dto.PresignedUrlResponse;
import by.marketplace.inspector.dto.RegisterCarReportRequest;
import by.marketplace.jooq.tables.records.ModerationLogRecord;
import by.marketplace.jooq.tables.records.ReportsRecord;
import by.marketplace.utils.InspectorUtils;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Интеграционные тесты модерации (admin/reports):
 * аутентификация, GET-эндпоинты и бизнес-логика approve / revise / delete.
 *
 * ФИКСТУРЫ (общие хелперы):
 *   - adminHeaders()           — JWT админа через реальный /admin/auth/login
 *   - createPendingReviewReport() — отчёт доведён до статуса pending_review
 *   - uploadAndConfirmVideo()  — пресайнд + PUT в MinIO + confirm
 */
public class ModerationFlowTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "password";
    private static final String ADMIN_TOTP_SECRET = "secret";

    private final TestRestTemplate restTemplate;
    private final DSLContext dsl;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TestNotificationSender notificationSender;

    @MockitoBean
    private AvByParser avByParser;

    @Autowired
    public ModerationFlowTest(TestRestTemplate restTemplate,
                              DSLContext dsl,
                              JwtService jwtService,
                              PasswordEncoder passwordEncoder, TestNotificationSender notificationSender) {
        this.restTemplate = restTemplate;
        this.dsl = dsl;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.notificationSender = notificationSender;
    }

    @BeforeEach
    void setUp() throws Exception {
        Mockito.when(avByParser.parse(anyString()))
                .thenReturn(new CarParseData("12345678901234567", "Toyota", "Camry", 2020));

        dsl.truncate(REPORTS).cascade().execute();
        dsl.truncate(CARS).cascade().execute();
        dsl.truncate(INSPECTORS).cascade().execute();
        dsl.truncate(ADMINS).cascade().execute();
        dsl.truncate(REPORT_REQUESTS).cascade().execute();
        dsl.truncate(USERS).cascade().execute();

        dsl.insertInto(INSPECTORS)
                .set(INSPECTORS.TELEGRAM_USER_ID, InspectorUtils.USER_ID)
                .set(INSPECTORS.FULL_NAME, InspectorUtils.FIRST_NAME)
                .set(INSPECTORS.PHONE, "375291234567")
                .set(INSPECTORS.EMAIL, "test@test.com")
                .execute();

        dsl.insertInto(ADMINS)
                .set(ADMINS.EMAIL, ADMIN_EMAIL)
                .set(ADMINS.PASSWORD_HASH, passwordEncoder.encode(ADMIN_PASSWORD))
                .set(ADMINS.TOTP_SECRET, ADMIN_TOTP_SECRET)
                .execute();

        notificationSender.getMessages().clear();
    }

    // ------------------------------------------------------------------
    // Хелперы (фикстуры)
    // ------------------------------------------------------------------

    /** Логин админа через /admin/auth/login и возврат Bearer-заголовков. */
    private HttpHeaders adminHeaders() throws CodeGenerationException {
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        String code = codeGenerator.generate(ADMIN_TOTP_SECRET, Instant.now().getEpochSecond() / 30);

        ResponseEntity<AdminAuthResponse> login = restTemplate.postForEntity(
                URI.create("/admin/auth/login"),
                new AdminLoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD, code),
                AdminAuthResponse.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().accessToken());
        return headers;
    }

    /** JWT с ролью BUYER — для проверки 403. */
    private HttpHeaders buyerHeaders() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "buyer@example.com", "BUYER");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /** Регистрирует отчёт, заполняет секции, заливает видео, ставит вывод, отправляет на модерацию. */
    private UUID createPendingReviewReport() throws Exception {
        HttpHeaders inspectorHeaders = inspectorHeaders();

        var request = new RegisterCarReportRequest("https://www.av.by/auto/toyota/camry/12345678901234567");
        ResponseEntity<CreateReportResponse> created = restTemplate.exchange(
                "/inspector/reports",
                HttpMethod.POST,
                new HttpEntity<>(request, inspectorHeaders),
                CreateReportResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID reportId = created.getBody().reportId();

        var sectionRequest = new UpdateSectionRequest(
                "test summary",
                List.of(new SectionItemInput("brakes", ItemStatus.BAD, "test"),
                        new SectionItemInput("engine", ItemStatus.OK, "test")));

        List<UUID> sectionIds = dsl.select(REPORT_SECTION.ID)
                .from(REPORT_SECTION)
                .where(REPORT_SECTION.REPORT_ID.eq(reportId))
                .fetch(REPORT_SECTION.ID);

        for (UUID sectionId : sectionIds) {
            ResponseEntity<Void> section = restTemplate.exchange(
                    "/inspector/reports/{reportId}/sections/{sectionId}",
                    HttpMethod.PUT,
                    new HttpEntity<>(sectionRequest, inspectorHeaders),
                    Void.class,
                    reportId, sectionId);
            assertThat(section.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        uploadAndConfirmVideo(reportId, inspectorHeaders);

        var conclusion = new UpdateConclusionRequest("Good condition overall", 150000L);
        ResponseEntity<Void> conclusionResponse = restTemplate.exchange(
                "/inspector/reports/{reportId}/conclusion",
                HttpMethod.PUT,
                new HttpEntity<>(conclusion, inspectorHeaders),
                Void.class,
                reportId);
        assertThat(conclusionResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> submit = restTemplate.exchange(
                "/inspector/reports/{reportId}/submit",
                HttpMethod.POST,
                new HttpEntity<>(inspectorHeaders),
                Void.class,
                reportId);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        return reportId;
    }

    private HttpHeaders inspectorHeaders() throws Exception {
        String data = InspectorUtils.buildValidInitData(System.currentTimeMillis() / 1000, InspectorUtils.USER_ID);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Telegram-Data", data);
        return headers;
    }

    private void uploadAndConfirmVideo(UUID reportId, HttpHeaders inspectorHeaders) {
        var presignedRequest = new PresignedUrlRequest(null, "clip.mp4", "video/mp4");
        ResponseEntity<PresignedUrlResponse> presigned = restTemplate.exchange(
                "/inspector/media/presigned-url/{reportId}",
                HttpMethod.POST,
                new HttpEntity<>(presignedRequest, inspectorHeaders),
                PresignedUrlResponse.class,
                reportId);
        assertThat(presigned.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders putHeaders = new HttpHeaders();
        putHeaders.setContentType(MediaType.parseMediaType("video/mp4"));
        byte[] fileBody = "fake-video-content".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<Void> putResponse = restTemplate.exchange(
                URI.create(presigned.getBody().uploadUrl()),
                HttpMethod.PUT,
                new HttpEntity<>(fileBody, putHeaders),
                Void.class);
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        var confirmRequest = new ConfirmUploadRequest(null, presigned.getBody().s3Key(), MediaKind.VIDEO);
        ResponseEntity<Void> confirm = restTemplate.exchange(
                "/inspector/media/confirm-upload/{reportId}",
                HttpMethod.POST,
                new HttpEntity<>(confirmRequest, inspectorHeaders),
                Void.class,
                reportId);
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ------------------------------------------------------------------
    // Базовые тесты
    // ------------------------------------------------------------------

    @Test
    void queue_withoutToken_shouldReturnUnauthorized() {
        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/admin/reports/queue",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void queue_withBuyerJwt_shouldReturnForbidden() {
        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/admin/reports/queue",
                HttpMethod.GET,
                new HttpEntity<>(buyerHeaders()),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void queue_withAdminJwt_shouldReturnPendingReviewReport() throws Exception {
        UUID reportId = createPendingReviewReport();

        ResponseEntity<ModerationQueueItemDto[]> response = restTemplate.exchange(
                "/admin/reports/queue",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                ModerationQueueItemDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        List<ModerationQueueItemDto> queue = Arrays.asList(response.getBody());
        assertThat(queue)
                .extracting(ModerationQueueItemDto::reportId)
                .contains(reportId);
    }

    @Test
    void getReportDetail_withAdminJwt_shouldReturnReportAndEmptyHistory() throws Exception {
        UUID reportId = createPendingReviewReport();

        ResponseEntity<AdminReportDetailDto> response = restTemplate.exchange(
                "/admin/reports/{id}",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                AdminReportDetailDto.class,
                reportId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().report()).isNotNull();
        assertThat(response.getBody().history()).isEmpty();
    }

    @Test
    void approveReport_withAdminJwt_shouldPublishAndNotify() throws Exception {
        UUID reportId = createPendingReviewReport();

        // покупатель должен существовать ДО approve — approve читает report_requests внутри себя
        ReportsRecord report = dsl.selectFrom(REPORTS)
                .where(REPORTS.ID.eq(reportId))
                .fetchOne();

        UUID buyerId = dsl.insertInto(USERS)
                .set(USERS.EMAIL, "buyer@example.com")
                .returning(USERS.ID)
                .fetchOne(USERS.ID);

        dsl.insertInto(REPORT_REQUESTS)
                .set(REPORT_REQUESTS.CAR_ID, report.getCarId())
                .set(REPORT_REQUESTS.BUYER_ID, buyerId)
                .set(REPORT_REQUESTS.EMAIL, "buyer@example.com")
                .execute();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/admin/reports/{id}/approve",
                HttpMethod.POST,
                new HttpEntity<>(adminHeaders()),
                Void.class,
                reportId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ReportsRecord approved = dsl.selectFrom(REPORTS)
                .where(REPORTS.ID.eq(reportId))
                .fetchOne();

        ModerationLogRecord moderationLog = dsl.selectFrom(MODERATION_LOG)
                .where(MODERATION_LOG.REPORT_ID.eq(reportId))
                .fetchOne();

        assertThat(approved.getStatus()).isEqualTo(ReportStatus.PUBLISHED.getCode());
        assertThat(moderationLog.getAction()).isEqualTo("approve");
        assertThat(moderationLog.getReportId()).isEqualTo(reportId);

        assertThat(notificationSender.getMessages()).containsExactly(
                "test@test.com: Ваш отчёт одобрен и опубликован",
                "buyer@example.com: Ваш запрос на отчёт был одобрен"
        );
    }

    @Test
    void approveReport_secondApprove_shouldReturnConflict() throws Exception {
        UUID reportId = createPendingReviewReport();

        ResponseEntity<Void> first = restTemplate.exchange(
                "/admin/reports/{id}/approve",
                HttpMethod.POST,
                new HttpEntity<>(adminHeaders()),
                Void.class,
                reportId);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ProblemDetail> second = restTemplate.exchange(
                "/admin/reports/{id}/approve",
                HttpMethod.POST,
                new HttpEntity<>(adminHeaders()),
                ProblemDetail.class,
                reportId);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().getProperties().get("errorCode"))
                .isEqualTo(ErrorCode.REPORT_NOT_PENDING_REVIEW.toString());
    }

    @Test
    void reviseReport_shouldSetRevisionRequired_allowEditing_andResubmit() throws Exception {
        UUID reportId = createPendingReviewReport();

        ResponseEntity<Void> revise = restTemplate.exchange(
                "/admin/reports/{id}/revise",
                HttpMethod.POST,
                new HttpEntity<>(new ReviseReportRequest("Нужно больше фото"), adminHeaders()),
                Void.class,
                reportId);

        assertThat(revise.getStatusCode()).isEqualTo(HttpStatus.OK);

        // статус -> revision_required
        String status = dsl.select(REPORTS.STATUS).from(REPORTS)
                .where(REPORTS.ID.eq(reportId))
                .fetchOne(REPORTS.STATUS);
        assertThat(status).isEqualTo(ReportStatus.REVISION_REQUIRED.getCode());

        // причина сохранилась в moderation_log
        ModerationLogRecord log = dsl.selectFrom(MODERATION_LOG)
                .where(MODERATION_LOG.REPORT_ID.eq(reportId))
                .fetchOne();
        assertThat(log.getAction()).isEqualTo("revise");
        assertThat(log.getReason()).isEqualTo("Нужно больше фото");

        // инспектор получил уведомление с причиной
        assertThat(notificationSender.getMessages()).containsExactly(
                "test@test.com: Отчёт требует правок: Нужно больше фото"
        );

        // requireEditableReport пропускает revision_required -> правка проходит
        HttpHeaders inspectorHeaders = inspectorHeaders();
        var conclusion = new UpdateConclusionRequest("Updated conclusion", 200000L);
        ResponseEntity<Void> edit = restTemplate.exchange(
                "/inspector/reports/{reportId}/conclusion",
                HttpMethod.PUT,
                new HttpEntity<>(conclusion, inspectorHeaders),
                Void.class,
                reportId);
        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // повторный submit снова переводит в pending_review
        ResponseEntity<Void> submit = restTemplate.exchange(
                "/inspector/reports/{reportId}/submit",
                HttpMethod.POST,
                new HttpEntity<>(inspectorHeaders),
                Void.class,
                reportId);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        String resubmitted = dsl.select(REPORTS.STATUS).from(REPORTS)
                .where(REPORTS.ID.eq(reportId))
                .fetchOne(REPORTS.STATUS);
        assertThat(resubmitted).isEqualTo(ReportStatus.PENDING_REVIEW.getCode());
    }

    @Test
    void deleteReport_shouldSoftDelete_hiddenFromInspector_visibleToAdmin() throws Exception {
        UUID reportId = createPendingReviewReport();

        ResponseEntity<Void> delete = restTemplate.exchange(
                "/admin/reports/{id}/delete",
                HttpMethod.POST,
                new HttpEntity<>(new DeleteReportRequest("Мошенничество"), adminHeaders()),
                Void.class,
                reportId);

        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.OK);

        // soft delete: deleted_at проставлен, статус не меняется
        OffsetDateTime deletedAt = dsl.select(REPORTS.DELETED_AT).from(REPORTS)
                .where(REPORTS.ID.eq(reportId))
                .fetchOne(REPORTS.DELETED_AT);
        assertThat(deletedAt).isNotNull();

        // инспектор больше не видит отчёт
        HttpHeaders inspectorHeaders = inspectorHeaders();
        ResponseEntity<ProblemDetail> inspectorGet = restTemplate.exchange(
                "/inspector/reports/{reportId}",
                HttpMethod.GET,
                new HttpEntity<>(inspectorHeaders),
                ProblemDetail.class,
                reportId);
        assertThat(inspectorGet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(inspectorGet.getBody().getProperties().get("errorCode"))
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND.toString());

        // админ видит отчёт для аудита
        ResponseEntity<AdminReportDetailDto> adminGet = restTemplate.exchange(
                "/admin/reports/{id}",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                AdminReportDetailDto.class,
                reportId);
        assertThat(adminGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        // инспектор получил уведомление о удалении
        assertThat(notificationSender.getMessages()).containsExactly(
                "test@test.com: Ваш отчёт удалён: Мошенничество"
        );

        // повторный delete -> 409
        ResponseEntity<ProblemDetail> secondDelete = restTemplate.exchange(
                "/admin/reports/{id}/delete",
                HttpMethod.POST,
                new HttpEntity<>(new DeleteReportRequest("again"), adminHeaders()),
                ProblemDetail.class,
                reportId);
        assertThat(secondDelete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondDelete.getBody().getProperties().get("errorCode"))
                .isEqualTo(ErrorCode.REPORT_ALREADY_DELETED.toString());
    }
}