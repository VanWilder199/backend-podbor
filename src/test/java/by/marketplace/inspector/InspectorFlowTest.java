package by.marketplace.inspector;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.TestNotificationSender;
import by.marketplace.inspector.dto.InspectorDto;
import by.marketplace.inspector.dto.RegisterInspectorRequest;
import by.marketplace.jooq.Tables;
import by.marketplace.utils.InspectorUtils;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import org.springframework.http.*;


import static by.marketplace.jooq.Tables.INSPECTORS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class InspectorFlowTest extends AbstractIntegrationTest {
    private final TestRestTemplate restTemplate;
    private final TestNotificationSender notificationSender;
    private final DSLContext dsl;


    @Autowired
    InspectorFlowTest(TestRestTemplate restTemplate,
                      TestNotificationSender notificationSender,
                      DSLContext dsl) {
        this.restTemplate = restTemplate;
        this.notificationSender = notificationSender;
        this.dsl = dsl;
    }

    @BeforeEach
    void setUp() {
        dsl.truncate(Tables.INSPECTORS).cascade().execute();
    }


    @Test
    void testAuthFlowUnAuthorized() {

        var authResponse = restTemplate.getForEntity(
                "/inspector/",
                InspectorDto.class
        );
        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registerAuthFlowTestSuccess() throws Exception {
        String data = InspectorUtils.buildValidInitData(System.currentTimeMillis() / 1000);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Telegram-Data", data);

        var request = new RegisterInspectorRequest(InspectorUtils.FIRST_NAME,"375291234567", "test@test.com");

        HttpEntity<RegisterInspectorRequest> requestHttpEntity = new HttpEntity<>(request,headers);

        ResponseEntity<InspectorDto> response = restTemplate.exchange(
                "/inspector/register",
                HttpMethod.POST,
                requestHttpEntity,
                InspectorDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().fullName()).isEqualTo(InspectorUtils.FIRST_NAME);
    }

    @Test
    void registerTwice_returnsConflict() throws Exception {
        String data = InspectorUtils.buildValidInitData(System.currentTimeMillis() / 1000);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Telegram-Data", data);

        var request = new RegisterInspectorRequest(InspectorUtils.FIRST_NAME,"375291234567", "test@test.com");

        HttpEntity<RegisterInspectorRequest> requestHttpEntity = new HttpEntity<>(request,headers);

        ResponseEntity<InspectorDto> response = restTemplate.exchange(
                "/inspector/register",
                HttpMethod.POST,
                requestHttpEntity,
                InspectorDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().fullName()).isEqualTo(InspectorUtils.FIRST_NAME);

        ResponseEntity<String> response2 = restTemplate.exchange(
                "/inspector/register",
                HttpMethod.POST,
                requestHttpEntity,
                String.class
        );

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    }

    @Test
    void getInspectorAuthFlowTestSuccess() throws Exception {

        String data = InspectorUtils.buildValidInitData(System.currentTimeMillis() / 1000);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Telegram-Data", data);

        var request = new RegisterInspectorRequest(InspectorUtils.FIRST_NAME,"375291234567", "test@test.com");

        dsl.insertInto(INSPECTORS)
                .set(INSPECTORS.TELEGRAM_USER_ID, InspectorUtils.USER_ID)
                .set(INSPECTORS.FULL_NAME, request.fullName())
                .set(INSPECTORS.PHONE, request.phone())
                .set(INSPECTORS.EMAIL, request.email())
                .returning()
                .fetchOptional();

        HttpEntity<RegisterInspectorRequest> requestHttpEntity = new HttpEntity<>(request,headers);

        ResponseEntity<InspectorDto> response = restTemplate.exchange(
                "/inspector/",
                HttpMethod.GET,
                requestHttpEntity,
                InspectorDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().fullName()).isEqualTo(InspectorUtils.FIRST_NAME);
        assertThat(response.getBody().phone()).isEqualTo("375291234567");
        assertThat(response.getBody().email()).isEqualTo("test@test.com");
    }

    @Test
    void getInspectorAuthFlowTestFail() throws Exception {

        String data = InspectorUtils.buildValidInitData(System.currentTimeMillis() / 1000);

        String invalidData = data.replace("hash=", "hash=invalid");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Telegram-Data", invalidData);

        var request = new RegisterInspectorRequest(InspectorUtils.FIRST_NAME,"375291234567", "test@test.com");

        dsl.insertInto(INSPECTORS)
                .set(INSPECTORS.TELEGRAM_USER_ID, InspectorUtils.USER_ID)
                .set(INSPECTORS.FULL_NAME, request.fullName())
                .set(INSPECTORS.PHONE, request.phone())
                .set(INSPECTORS.EMAIL, request.email())
                .returning()
                .fetchOptional();

        HttpEntity<RegisterInspectorRequest> requestHttpEntity = new HttpEntity<>(request,headers);

        ResponseEntity<InspectorDto> response = restTemplate.exchange(
                "/inspector/",
                HttpMethod.GET,
                requestHttpEntity,
                InspectorDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

}
