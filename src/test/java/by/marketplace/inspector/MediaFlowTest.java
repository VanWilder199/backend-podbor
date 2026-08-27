package by.marketplace.inspector;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.car.AvByParser;
import by.marketplace.car.dto.CarParseData;
import by.marketplace.inspector.dto.*;
import by.marketplace.jooq.tables.records.CarsRecord;
import by.marketplace.jooq.tables.records.ReportMediaRecord;
import by.marketplace.jooq.tables.records.ReportSectionRecord;
import by.marketplace.jooq.tables.records.ReportsRecord;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;
import static by.marketplace.jooq.Tables.REPORTS;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

public class MediaFlowTest  extends AbstractIntegrationTest {
    private final TestRestTemplate restTemplate;
    private final DSLContext dsl;

    private final HttpHeaders headers = new HttpHeaders();
    private ReportSectionRecord sectionId;
    private UUID reportId;

    @MockitoBean
    private AvByParser avByParser;

    @Autowired
    public MediaFlowTest(TestRestTemplate restTemplate, DSLContext dslContext) {
        this.restTemplate = restTemplate;
        this.dsl = dslContext;
    }

    @BeforeEach
    void setUp() throws Exception {
        Mockito.when(avByParser.parse(anyString())).thenReturn(new CarParseData("12345678901234567", "Toyota", "Camry", 2020));

        dsl.truncate(INSPECTORS).cascade().execute();
        dsl.truncate(CARS).cascade().execute();

        String data = InspectorUtils.buildValidInitData(System.currentTimeMillis() / 1000);

        headers.set("X-Telegram-Data", data);

        var dataFillTable = new RegisterInspectorRequest(InspectorUtils.FIRST_NAME,"375291234567", "test@test.com");

        dsl.insertInto(INSPECTORS)
                .set(INSPECTORS.TELEGRAM_USER_ID, InspectorUtils.USER_ID)
                .set(INSPECTORS.FULL_NAME, dataFillTable.fullName())
                .set(INSPECTORS.PHONE, dataFillTable.phone())
                .set(INSPECTORS.EMAIL, dataFillTable.email())
                .returning()
                .fetchOptional();

        var request = new RegisterCarReportRequest("https://www.av.by/auto/toyota/camry/12345678901234567");

        HttpEntity<RegisterCarReportRequest> requestHttpEntity = new HttpEntity<>(request,headers);

        ResponseEntity<CreateReportResponse> response = restTemplate.exchange(
                "/inspector/reports",
                HttpMethod.POST,
                requestHttpEntity,
                CreateReportResponse.class
        );

        reportId = response.getBody().reportId();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);


        CarsRecord cars = dsl.selectFrom(CARS)
                .where(CARS.AVBY_LISTING_URL.eq(request.avbyUrl()))
                .fetchOne();

        ReportsRecord reports = dsl.selectFrom(REPORTS)
                .where(REPORTS.ID.eq(response.getBody().reportId()))
                .fetchOne();

        UUID inspectorId = dsl.select(INSPECTORS.ID)
                .from(INSPECTORS)
                .where(INSPECTORS.TELEGRAM_USER_ID.eq(InspectorUtils.USER_ID))
                .fetchOne(INSPECTORS.ID);

         sectionId = dsl.selectFrom(REPORT_SECTION)
                .where(REPORT_SECTION.REPORT_ID.eq(reports.getId())).fetchAny();

    }

    @Test
    void presignedUrl_created() throws Exception {
        var request = new PresignedUrlRequest(
                this.sectionId.getId(),
                "test.jpg",
                "image/jpeg"
        );

        HttpEntity<PresignedUrlRequest> requestHttpEntity = new HttpEntity<>(request,headers);

        ResponseEntity<PresignedUrlResponse> response = restTemplate.exchange(
                "/inspector/media/presigned-url/{reportId}",
                HttpMethod.POST,
                requestHttpEntity,
                PresignedUrlResponse.class,
                reportId
        );


        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders putHeaders = new HttpHeaders();
        putHeaders.setContentType(MediaType.IMAGE_JPEG);

        byte[] body = "fake-image-content".getBytes(StandardCharsets.UTF_8);
        HttpEntity<byte[]> putEntity = new HttpEntity<>(body, putHeaders);

        ResponseEntity<Void> putResponse = restTemplate.exchange(
                URI.create(response.getBody().uploadUrl()),
                HttpMethod.PUT,
                putEntity,
                Void.class
        );

        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        var confirmRequest = new ConfirmUploadRequest(
                this.sectionId.getId(),
                response.getBody().s3Key(),
                MediaKind.PHOTO
        );

        HttpEntity<ConfirmUploadRequest> confirmEntity = new HttpEntity<>(confirmRequest,headers);

        ResponseEntity<Void> confirmRespnose = restTemplate.exchange(
                "/inspector/media/confirm-upload/{reportId}",
                HttpMethod.POST,
                confirmEntity,
                Void.class,
                reportId
        );

        assertThat(confirmRespnose.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ReportMediaRecord media = dsl.selectFrom(REPORT_MEDIA)
                .where(REPORT_MEDIA.REPORT_ID.eq(reportId))
                .fetchOne();

        assertThat(media.getStatus()).isEqualTo("uploaded");
        assertThat(media.getS3Key()).isEqualTo(response.getBody().s3Key());
        assertThat(media.getSectionId()).isEqualTo(sectionId.getId());
        assertThat(media.getKind()).isEqualTo("PHOTO");
    }

    @Test
    void confirmUpload_notFound_whenFileNeverUploaded() throws Exception {
        var request = new ConfirmUploadRequest(
                sectionId.getId(),
                "reports/nonexistent-key.jpg",
                MediaKind.PHOTO
        );

        HttpEntity<ConfirmUploadRequest> requestHttpEntity = new HttpEntity<>(request,headers);

        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/inspector/media/confirm-upload/{reportId}",
                HttpMethod.POST,
                requestHttpEntity,
                ProblemDetail.class,
                reportId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("errorCode")).isEqualTo(ErrorCode.MEDIA_NOT_FOUND.toString());

    }

    @Test
    void presignedUrl_created_video() {
        var request = new PresignedUrlRequest(
                null,
                "clip.mp4",
                    "video/mp4"
        );

        HttpEntity<PresignedUrlRequest> requestHttpEntity = new HttpEntity<>(request,headers);



        ResponseEntity<PresignedUrlResponse> response = restTemplate.exchange(
                "/inspector/media/presigned-url/{reportId}",
                HttpMethod.POST,
                requestHttpEntity,
                PresignedUrlResponse.class,
                reportId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().s3Key()).isEqualTo("reports/" + reportId + "/clip.mp4");

        MediaType videoType = MediaType.parseMediaType("video/mp4");

        HttpHeaders putHeaders = new HttpHeaders();
        putHeaders.setContentType(videoType);

        byte[] body = "fake-image-content".getBytes(StandardCharsets.UTF_8);
        HttpEntity<byte[]> putEntity = new HttpEntity<>(body, putHeaders);

        ResponseEntity<Void> putResponse = restTemplate.exchange(
                URI.create(response.getBody().uploadUrl()),
                HttpMethod.PUT,
                putEntity,
                Void.class
        );

        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        var confirmRequest = new ConfirmUploadRequest(
                null,
                response.getBody().s3Key(),
                MediaKind.VIDEO
        );

        HttpEntity<ConfirmUploadRequest> confirmEntity = new HttpEntity<>(confirmRequest,headers);

        ResponseEntity<Void> confirmRespnose = restTemplate.exchange(
                "/inspector/media/confirm-upload/{reportId}",
                HttpMethod.POST,
                confirmEntity,
                Void.class,
                reportId
        );

        assertThat(confirmRespnose.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ReportMediaRecord media = dsl.selectFrom(REPORT_MEDIA)
                .where(REPORT_MEDIA.REPORT_ID.eq(reportId))
                .fetchOne();

        assertThat(media.getStatus()).isEqualTo("pending");
        assertThat(media.getKind()).isEqualTo("VIDEO");
        assertThat(media.getSectionId()).isNull();
    }


}
