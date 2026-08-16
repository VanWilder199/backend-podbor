package by.marketplace.inspector;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.TestNotificationSender;
import by.marketplace.inspector.dto.InspectorDto;
import by.marketplace.jooq.Tables;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import org.springframework.http.HttpStatus;


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

}
