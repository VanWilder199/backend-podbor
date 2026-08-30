package by.marketplace.inspector;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.car.AvByParser;
import by.marketplace.car.dto.*;
import by.marketplace.car.enums.ItemStatus;
import by.marketplace.inspector.dto.*;
import by.marketplace.jooq.tables.records.ReportMediaRecord;
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
import java.util.List;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

public class ReportContentFlowTest  extends AbstractIntegrationTest {
    private final TestRestTemplate restTemplate;
    private final DSLContext dsl;

    private final HttpHeaders headers = new HttpHeaders();
    private UUID sectionId;
    private UUID reportId;

    @MockitoBean
    private AvByParser avByParser;


    @Autowired
    public ReportContentFlowTest(TestRestTemplate restTemplate, DSLContext dsl) {
        this.restTemplate = restTemplate;
        this.dsl = dsl;
    }

    @BeforeEach
    void setUp() throws Exception {
        Mockito.when(avByParser.parse(anyString())).thenReturn(new CarParseData("12345678901234567", "Toyota", "Camry", 2020));

        dsl.truncate(INSPECTORS).cascade().execute();
        dsl.truncate(CARS).cascade().execute();

        String data = InspectorUtils.buildValidInitData(System.currentTimeMillis() / 1000, InspectorUtils.USER_ID);

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

        sectionId = dsl.selectFrom(REPORT_SECTION)
                .where(REPORT_SECTION.REPORT_ID.eq(response.getBody().reportId())).fetchAny(REPORT_SECTION.ID);
    }

    private <T> ResponseEntity<T> putSection(UUID sectionId, UpdateSectionRequest request, HttpHeaders headers, Class<T> responseType) {
        HttpEntity<UpdateSectionRequest> requestHttpEntity = new HttpEntity<>(request, headers);

        return restTemplate.exchange(
                "/inspector/reports/{reportId}/sections/{sectionId}",
                HttpMethod.PUT,
                requestHttpEntity,
                responseType,
                reportId,
                sectionId
        );
    }

    private <T> ResponseEntity<T> putPaintMeasurements(UpdatePaintMeasurementsRequest request, Class<T> responseType) {
        HttpEntity<UpdatePaintMeasurementsRequest> requestHttpEntity = new HttpEntity<>(request, headers);

        return restTemplate.exchange(
                "/inspector/reports/{reportId}/paint",
                HttpMethod.PUT,
                requestHttpEntity,
                responseType,
                reportId
        );
    }

    private ResponseEntity<Void> putConclusion(UpdateConclusionRequest request) {
        HttpEntity<UpdateConclusionRequest> requestHttpEntity = new HttpEntity<>(request, headers);

        return restTemplate.exchange(
                "/inspector/reports/{reportId}/conclusion",
                HttpMethod.PUT,
                requestHttpEntity,
                Void.class,
                reportId
        );
    }

    private <T> ResponseEntity<T> submitReport(Class<T> responseType) {
        return restTemplate.exchange(
                "/inspector/reports/{reportId}/submit",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                responseType,
                reportId
        );
    }

    private String uploadAndConfirmMedia(UUID sectionId, String fileName, String contentType, MediaKind kind) {
        var presignedRequest = new PresignedUrlRequest(sectionId, fileName, contentType);
        HttpEntity<PresignedUrlRequest> presignedRequestHttpEntity = new HttpEntity<>(presignedRequest, headers);

        ResponseEntity<PresignedUrlResponse> presignedResponse = restTemplate.exchange(
                "/inspector/media/presigned-url/{reportId}",
                HttpMethod.POST,
                presignedRequestHttpEntity,
                PresignedUrlResponse.class,
                reportId
        );

        assertThat(presignedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        PresignedUrlResponse presignedBody = presignedResponse.getBody();

        HttpHeaders putHeaders = new HttpHeaders();
        putHeaders.setContentType(MediaType.parseMediaType(contentType));

        byte[] fileBody = "fake-image-content".getBytes(StandardCharsets.UTF_8);
        HttpEntity<byte[]> putEntity = new HttpEntity<>(fileBody, putHeaders);

        ResponseEntity<Void> putResponse = restTemplate.exchange(
                URI.create(presignedBody.uploadUrl()),
                HttpMethod.PUT,
                putEntity,
                Void.class
        );

        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        var confirmRequest = new ConfirmUploadRequest(sectionId, presignedBody.s3Key(), kind);
        HttpEntity<ConfirmUploadRequest> confirmEntity = new HttpEntity<>(confirmRequest, headers);

        ResponseEntity<Void> confirmResponse = restTemplate.exchange(
                "/inspector/media/confirm-upload/{reportId}",
                HttpMethod.POST,
                confirmEntity,
                Void.class,
                reportId
        );

        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        return presignedBody.s3Key();
    }

    private List<SectionItemInput> fetchSectionItems(UUID sectionId) {
        return dsl.select(
                        REPORT_SECTION_ITEM.ITEM_KEY,
                        REPORT_SECTION_ITEM.STATUS,
                        REPORT_SECTION_ITEM.COMMENT)
                .from(REPORT_SECTION_ITEM)
                .where(REPORT_SECTION_ITEM.SECTION_ID.eq(sectionId))
                .fetch(r -> new SectionItemInput(
                        r.value1(),
                        ItemStatus.valueOf(r.value2()),
                        r.value3()
                ));
    }

    private UUID fetchPanelId(String code) {
        return dsl.select(PAINT_PANEL.ID)
                .from(PAINT_PANEL)
                .where(PAINT_PANEL.CODE.eq(code))
                .fetchOne(PAINT_PANEL.ID);
    }

    private List<PaintMeasurementInput> fetchPaintMeasurements() {
        return dsl.select(
                        PAINT_MEASUREMENT.PANEL_ID,
                        PAINT_MEASUREMENT.SPOT,
                        PAINT_MEASUREMENT.THICKNESS_UM,
                        PAINT_MEASUREMENT.NOTE)
                .from(PAINT_MEASUREMENT)
                .where(PAINT_MEASUREMENT.REPORT_ID.eq(reportId))
                .fetch(r -> new PaintMeasurementInput(
                        r.value1(),
                        r.value2(),
                        r.value3(),
                        r.value4()
                ));
    }

    @Test
    public void  updateSection_Success() {
        SectionItemInput sectionItemInput = new SectionItemInput("brakes", ItemStatus.BAD, "test");
        SectionItemInput sectionItemInput2 = new SectionItemInput("engine", ItemStatus.OK, "test");

        List<SectionItemInput> sectionItemInputs = List.of(sectionItemInput, sectionItemInput2);
        var request = new UpdateSectionRequest("test", sectionItemInputs);

        ResponseEntity<Void> response = putSection(sectionId, request, headers, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(fetchSectionItems(sectionId)).containsExactlyInAnyOrderElementsOf(sectionItemInputs);

        SectionItemInput sectionItemInput3 = new SectionItemInput("suspension", ItemStatus.WARN, "test");
        SectionItemInput sectionItemInput4 = new SectionItemInput("engine", ItemStatus.WARN, "test");

        List<SectionItemInput> sectionItemInputs2 = List.of(sectionItemInput3, sectionItemInput4);
        var request2 = new UpdateSectionRequest("test", sectionItemInputs2);

        ResponseEntity<Void> response2 = putSection(sectionId, request2, headers, Void.class);

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(fetchSectionItems(sectionId)).containsExactlyInAnyOrderElementsOf(sectionItemInputs2);
    }

    @Test
    public void  updateSection_Failed_whenInspectorIdWrong() throws Exception {
        dsl.insertInto(INSPECTORS)
                .set(INSPECTORS.TELEGRAM_USER_ID, InspectorUtils.SECOND_USER_ID)
                .set(INSPECTORS.FULL_NAME, "Other Inspector")
                .set(INSPECTORS.PHONE, "375291111111")
                .set(INSPECTORS.EMAIL, "other@test.com")
                .execute();

        HttpHeaders otherHeaders = new HttpHeaders();
        otherHeaders.set("X-Telegram-Data",
                InspectorUtils.buildValidInitData(System.currentTimeMillis() / 1000, InspectorUtils.SECOND_USER_ID));

        var request = new UpdateSectionRequest("test", List.of(new SectionItemInput("brakes", ItemStatus.BAD, "test")));

        ResponseEntity<ProblemDetail> response = putSection(sectionId, request, otherHeaders, ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getProperties().get("errorCode")).isEqualTo(ErrorCode.REPORT_ACCESS_DENIED.toString());
    }


    @Test
    public void updateSection_Failed_WhenSectionNotFound() {
        var request = new UpdateSectionRequest("test", List.of(new SectionItemInput("brakes", ItemStatus.BAD, "test")));

        ResponseEntity<ProblemDetail> response = putSection(new UUID(0, 0), request, headers, ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("errorCode")).isEqualTo(ErrorCode.SECTION_NOT_FOUND.toString());
    }

    @Test
    public void updatePaintMeasurements_success() {
        UUID panelId = fetchPanelId("HOOD");

        PaintMeasurementInput paintMeasurementInput = new PaintMeasurementInput(panelId, "test", 100, "good");
        List<PaintMeasurementInput> measurementInput = List.of(paintMeasurementInput);
        var request = new UpdatePaintMeasurementsRequest(measurementInput);

        ResponseEntity<Void> response = putPaintMeasurements(request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(fetchPaintMeasurements()).containsExactlyInAnyOrderElementsOf(measurementInput);
    }

    @Test
    public void updatePaintMeasurements_failed_whenPanelNotFound() {
        PaintMeasurementInput paintMeasurementInput = new PaintMeasurementInput(new UUID(0,0), "test", 100, "good");
        var request = new UpdatePaintMeasurementsRequest(List.of(paintMeasurementInput));

        ResponseEntity<ProblemDetail> response = putPaintMeasurements(request, ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("errorCode")).isEqualTo(ErrorCode.PANEL_NOT_FOUND.toString());
    }

    @Test
    public void updateConclusion_success() {
        var request = new UpdateConclusionRequest("Good condition overall", 150000L);

        ResponseEntity<Void> response = putConclusion(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var updated = dsl.select(REPORTS.CONCLUSION_TEXT, REPORTS.PRICE_BYN)
                .from(REPORTS)
                .where(REPORTS.ID.eq(reportId))
                .fetchOne();

        assertThat(updated.value1()).isEqualTo("Good condition overall");
        assertThat(updated.value2()).isEqualTo(150000L);
    }

    @Test
    public void submitForModeration_failed_whenReportIncomplete() {
        ResponseEntity<ProblemDetail> response = submitReport(ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties().get("errorCode")).isEqualTo(ErrorCode.REPORT_INCOMPLETE.toString());
    }

    @Test
    public void submitForModeration_success() {
        List<UUID> sectionIds = dsl.select(REPORT_SECTION.ID)
                .from(REPORT_SECTION)
                .where(REPORT_SECTION.REPORT_ID.eq(reportId))
                .fetch(REPORT_SECTION.ID);

        var request = new UpdateSectionRequest(
                "test",
                List.of(new SectionItemInput("brakes", ItemStatus.BAD, "test"),
                        new SectionItemInput("engine", ItemStatus.OK, "test"))
        );

        for (UUID currentSectionId : sectionIds) {
            ResponseEntity<Void> response = putSection(currentSectionId, request, headers, Void.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        String s3Key = uploadAndConfirmMedia(null, "clip.mp4", "video/mp4", MediaKind.VIDEO);
        assertThat(s3Key).isEqualTo("reports/" + reportId + "/clip.mp4");

        ReportMediaRecord media = dsl.selectFrom(REPORT_MEDIA)
                .where(REPORT_MEDIA.REPORT_ID.eq(reportId))
                .fetchOne();

        assertThat(media.getStatus()).isEqualTo("pending");
        assertThat(media.getKind()).isEqualTo("VIDEO");
        assertThat(media.getSectionId()).isNull();

        ResponseEntity<Void> conclusionResponse = putConclusion(new UpdateConclusionRequest("Good condition overall", 150000L));
        assertThat(conclusionResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> submitResponse = submitReport(Void.class);
        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        String status = dsl.select(REPORTS.STATUS)
                .from(REPORTS)
                .where(REPORTS.ID.eq(reportId))
                .fetchOne(REPORTS.STATUS);

        assertThat(status).isEqualTo("pending_review");

        ResponseEntity<ProblemDetail> secondSubmitResponse = submitReport(ProblemDetail.class);

        assertThat(secondSubmitResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondSubmitResponse.getBody().getProperties().get("errorCode")).isEqualTo(ErrorCode.REPORT_NOT_EDITABLE.toString());
    }

    @Test
    public void getReport_success() {
        SectionItemInput badItem = new SectionItemInput("brakes", ItemStatus.BAD, "urgent");
        SectionItemInput okItem = new SectionItemInput("engine", ItemStatus.OK, "fine");

        var sectionRequest = new UpdateSectionRequest("test summary", List.of(badItem, okItem));
        ResponseEntity<Void> sectionResponse = putSection(sectionId, sectionRequest, headers, Void.class);

        assertThat(sectionResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        UUID panelId = fetchPanelId("HOOD");

        var paintRequest = new UpdatePaintMeasurementsRequest(
                List.of(new PaintMeasurementInput(panelId, "test", 100, "good"))
        );
        ResponseEntity<Void> paintResponse = putPaintMeasurements(paintRequest, Void.class);

        assertThat(paintResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        uploadAndConfirmMedia(sectionId, "test.jpg", "image/jpeg", MediaKind.PHOTO);

        ResponseEntity<ReportDto> response = restTemplate.exchange(
                "/inspector/reports/{reportId}",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                ReportDto.class,
                reportId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ReportDto body = response.getBody();

        assertThat(body.sections()).hasSize(7);

        ReportSectionDto targetSection = body.sections().stream()
                .filter(s -> s.id().equals(sectionId))
                .findFirst()
                .orElseThrow();

        assertThat(targetSection.summary()).isEqualTo("test summary");
        assertThat(targetSection.items()).containsExactlyInAnyOrder(
                new SectionItemDto("brakes", ItemStatus.BAD, "urgent"),
                new SectionItemDto("engine", ItemStatus.OK, "fine")
        );

        assertThat(targetSection.media()).hasSize(1);
        assertThat(targetSection.media().get(0).kind()).isEqualTo("PHOTO");

        assertThat(body.paintMeasurements()).hasSize(1);
        PaintMeasurementDto measurement = body.paintMeasurements().get(0);
        assertThat(measurement.panelCode()).isEqualTo("HOOD");
        assertThat(measurement.spot()).isEqualTo("test");
        assertThat(measurement.thicknessUm()).isEqualTo(100);
        assertThat(measurement.note()).isEqualTo("good");

        assertThat(body.stopFactors()).containsExactly("brakes");
    }
}
