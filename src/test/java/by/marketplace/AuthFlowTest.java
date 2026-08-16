package by.marketplace;

import by.marketplace.auth.dto.*;
import by.marketplace.jooq.Tables;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;


import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class AuthFlowTest extends AbstractIntegrationTest {

    private final TestRestTemplate restTemplate;
    private final TestNotificationSender notificationSender;
    private final DSLContext dsl;
    
    private final String destination = "+375295722007";

    @Autowired
    AuthFlowTest(TestRestTemplate restTemplate,
                 TestNotificationSender notificationSender, 
                 DSLContext dsl) {
        this.restTemplate = restTemplate;
        this.notificationSender = notificationSender;
        this.dsl = dsl;
    }

    @BeforeEach
    void setUp() {
        dsl.truncate(Tables.OTP_CODES, Tables.OTP_RATE_LIMITS).cascade().execute();
    }

    @Test
    void testFullAuthFlow() {
        // Шаг 1: POST /auth/otp/send → 202
        var sendRequest = new SendOtpRequest(null, destination);
        var sendResponse = restTemplate.postForEntity("/auth/otp/send", sendRequest, Void.class);
        assertThat(sendResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Шаг 2: Достать код из заглушки
        String code = notificationSender.getLastCode();
        assertThat(code).isNotNull().hasSize(6);

        // Шаг 3: POST /auth/otp/verify → 200 + tokens
        var verifyRequest = new VerifyOtpRequest(destination, Channel.SMS, code);
        var verifyResponse = restTemplate.postForEntity("/auth/otp/verify", verifyRequest, AuthResponse.class);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse auth = verifyResponse.getBody();
        assertThat(auth).isNotNull();
        assertThat(auth.accessToken()).isNotNull();
        assertThat(auth.refreshToken()).isNotNull();

        // Шаг 4: GET /users/me без токена → 401
        var unauthResponse = restTemplate.getForEntity("/users/me", Void.class);
        assertThat(unauthResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Шаг 5: POST /auth/refresh → 200
        var refreshRequest = new RefreshRequest(auth.refreshToken());
        var refreshResponse = restTemplate.postForEntity("/auth/refresh", refreshRequest, AuthResponse.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse newAuth = refreshResponse.getBody();
        assertThat(newAuth).isNotNull();
        assertThat(newAuth.refreshToken()).isNotEqualTo(auth.refreshToken());

        // Шаг 6: POST /auth/refresh тем же токеном → 401
        var replayResponse = restTemplate.postForEntity("/auth/refresh", refreshRequest, AuthResponse.class);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
