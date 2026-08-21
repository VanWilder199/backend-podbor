package by.marketplace.inspector;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.car.AvByParser;
import by.marketplace.car.dto.CarParseData;
import by.marketplace.car.enums.SectionKey;
import by.marketplace.inspector.dto.CreateReportResponse;
import by.marketplace.inspector.dto.RegisterCarReportRequest;
import by.marketplace.inspector.dto.RegisterInspectorRequest;
import by.marketplace.jooq.tables.records.CarsRecord;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

public class InspectorReportFlowTest  extends AbstractIntegrationTest {

    private final TestRestTemplate restTemplate;
    private final DSLContext dsl;

    @MockitoBean
    private AvByParser avByParser;


    @BeforeEach
    void setUp() {
        Mockito.when(avByParser.parse(anyString())).thenReturn(new CarParseData("12345678901234567", "Toyota", "Camry", 2020));

        dsl.truncate(INSPECTORS).cascade().execute();
        dsl.truncate(CARS).cascade().execute();
    }

    @Autowired
    InspectorReportFlowTest(TestRestTemplate restTemplate,
                 DSLContext dsl) {
        this.restTemplate = restTemplate;
        this.dsl = dsl;
    }

    @Test
    void createReport_notFoundInspector() throws Exception {
        String data = InspectorUtils.buildValidInitData(System.currentTimeMillis() / 1000);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Telegram-Data", data);

        var request = new RegisterCarReportRequest("https://www.av.by/auto/toyota/camry/12345678901234567");

        HttpEntity<RegisterCarReportRequest> requestHttpEntity = new HttpEntity<>(request,headers);

        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
                "/inspector/reports",
                HttpMethod.POST,
                requestHttpEntity,
                ProblemDetail.class
        );

        assertThat(response.getBody().getProperties().get("errorCode")).isEqualTo(ErrorCode.INSPECTOR_NOT_FOUND.toString());

    }

    @Test
    void createReport_success() throws Exception {
        String data = InspectorUtils.buildValidInitData(System.currentTimeMillis() / 1000);

        HttpHeaders headers = new HttpHeaders();
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

        List<ReportSectionRecord> reportSections = dsl.selectFrom(REPORT_SECTION)
                .where(REPORT_SECTION.REPORT_ID.eq(reports.getId()))
                .fetch();

        assertThat(cars.getMake()).isEqualTo("Toyota");
        assertThat(reports.getStatus()).isEqualTo("draft");
        assertThat(inspectorId).isEqualTo(reports.getInspectorId());
        assertThat(reports.getCarId()).isEqualTo(cars.getId());

        assertThat(reportSections).hasSize(7);

        assertThat(reportSections)
                .extracting(ReportSectionRecord::getOrderNo)
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7);

        String[] expectedSectionKeys = Arrays.stream(SectionKey.values())
                .map(Enum::name)
                .toArray(String[]::new);

        assertThat(reportSections)
                .extracting(ReportSectionRecord::getSectionKey)
                .containsExactlyInAnyOrder(expectedSectionKeys);

        assertThat(reportSections)
                .extracting(ReportSectionRecord::getSummary)
                .containsOnly("");
    }


}
