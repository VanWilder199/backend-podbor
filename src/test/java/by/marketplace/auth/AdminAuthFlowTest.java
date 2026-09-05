package by.marketplace.auth;

import by.marketplace.AbstractIntegrationTest;
import by.marketplace.auth.dto.*;
import by.marketplace.auth.service.JwtService;
import by.marketplace.jooq.tables.records.AdminsRecord;
import by.marketplace.shared.exception.ErrorCode;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static by.marketplace.jooq.Tables.ADMINS;
import static org.assertj.core.api.Assertions.assertThat;

public class AdminAuthFlowTest extends AbstractIntegrationTest {
    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;
    private final TestRestTemplate restTemplate;
    private final JwtService jwtService;

    @Autowired
    public AdminAuthFlowTest(DSLContext dsl, PasswordEncoder passwordEncoder, TestRestTemplate restTemplate, JwtService jwtService) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
        this.restTemplate = restTemplate;
        this.jwtService = jwtService;
    }

    @BeforeEach
    void setUp() {
        dsl.truncate("admins").execute();

        dsl.insertInto(ADMINS)
                .set(ADMINS.EMAIL, "admin@example.com")
                .set(ADMINS.PASSWORD_HASH, passwordEncoder.encode("password"))
                .execute();
    }


    @Test
    void login_shouldReturnError_totpNotConfirmed() {
        var request = new AdminLoginRequest("admin@example.com", "password", "123456");

        var authResponse = restTemplate.postForEntity(
                URI.create("/admin/auth/login"),
                request,
                ProblemDetail.class
        );

        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(authResponse.getBody().getProperties().get("errorCode")).isEqualTo(ErrorCode.ADMIN_TOTP_NOT_CONFIGURED.toString());

    }

    @Test
    void login_shouldReturnError_totpInvalid() {
        dsl.insertInto(ADMINS)
                .set(ADMINS.EMAIL, "admin2@example.com")
                .set(ADMINS.PASSWORD_HASH, passwordEncoder.encode("password"))
                .set(ADMINS.TOTP_SECRET, "secret")
                .execute();
        var request = new AdminLoginRequest("admin2@example.com", "password", "123456");

        var authResponse = restTemplate.postForEntity(
                URI.create("/admin/auth/login"),
                request,
                ProblemDetail.class
        );

        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(authResponse.getBody().getProperties().get("errorCode")).isEqualTo(ErrorCode.ADMIN_TOTP_INVALID.toString());

    }

    @Test
    void setup_totp_shouldReturnError_invalidCredentials() {
        dsl.insertInto(ADMINS)
                .set(ADMINS.EMAIL, "admin2@example.com")
                .set(ADMINS.PASSWORD_HASH, passwordEncoder.encode("password"))
                .execute();
        var request = new AdminSetupTotpRequest("admin2@example.com", "wrongPassword");

        var authResponse = restTemplate.postForEntity(
                URI.create("/admin/auth/setup-totp"),
                request,
                ProblemDetail.class
        );

        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(authResponse.getBody().getProperties().get("errorCode")).isEqualTo(ErrorCode.ADMIN_INVALID_CREDENTIALS.toString());

        AdminsRecord record = dsl.selectFrom(ADMINS)
                .where(ADMINS.EMAIL.eq("admin2@example.com"))
                .fetchOne();

        assertThat(record.getTotpSecret()).isNull();

    }

    @Test
    void setup_totp_shouldReturnSuccess() {
        dsl.insertInto(ADMINS)
                .set(ADMINS.EMAIL, "admin2@example.com")
                .set(ADMINS.PASSWORD_HASH, passwordEncoder.encode("password"))
                .execute();
        var request = new AdminSetupTotpRequest("admin2@example.com", "password");

        var authResponse = restTemplate.postForEntity(
                URI.create("/admin/auth/setup-totp"),
                request,
                AdminTotpSetupResponse.class
        );

        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authResponse.getBody().secret()).isNotNull();
        assertThat(authResponse.getBody().otpAuthUrl()).isNotNull();

        AdminsRecord record = dsl.selectFrom(ADMINS)
                .where(ADMINS.EMAIL.eq("admin2@example.com"))
                .fetchOne();

        assertThat(record.getTotpSecret()).isNotNull();

    }

    @Test
    void setup_totp_shouldReturnError_totpAlreadyConfigured() {
        dsl.insertInto(ADMINS)
                .set(ADMINS.EMAIL, "admin2@example.com")
                .set(ADMINS.PASSWORD_HASH, passwordEncoder.encode("password"))
                .set(ADMINS.TOTP_SECRET, "secret")
                .execute();
        var request = new AdminSetupTotpRequest("admin2@example.com", "password");

        var authResponse = restTemplate.postForEntity(
                URI.create("/admin/auth/setup-totp"),
                request,
                ProblemDetail.class
        );

        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(authResponse.getBody().getProperties().get("errorCode")).isEqualTo(ErrorCode.ADMIN_TOTP_ALREADY_CONFIGURED.toString());


        AdminsRecord record = dsl.selectFrom(ADMINS)
                .where(ADMINS.EMAIL.eq("admin2@example.com"))
                .fetchOne();

        assertThat(record.getTotpSecret()).isNotNull();

    }

    @Test
    void login_shouldReturnSuccess() throws CodeGenerationException {

        dsl.insertInto(ADMINS)
                .set(ADMINS.EMAIL, "admin2@example.com")
                .set(ADMINS.PASSWORD_HASH, passwordEncoder.encode("password"))
                .set(ADMINS.TOTP_SECRET, "secret")
                .execute();


        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        String code = codeGenerator.generate("secret", Instant.now().getEpochSecond() / 30);

        var request = new AdminLoginRequest("admin2@example.com", "password", code);

        var authResponse = restTemplate.postForEntity(
                URI.create("/admin/auth/login"),
                request,
                AdminAuthResponse.class
        );

        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authResponse.getBody().accessToken()).isNotNull();
        assertThat(authResponse.getBody().expiresIn()).isNotNull();

    }

    @Test
    void adminMe_shouldReturnUnauthorized_withoutToken() {
        var response = restTemplate.getForEntity("/admin/", Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminMe_shouldReturnForbidden_withBuyerJwt() {
        String buyerToken = jwtService.generateAccessToken(UUID.randomUUID(), "buyer@example.com", "BUYER");

        System.out.println(buyerToken + " buyerToken");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(buyerToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        var response = restTemplate.exchange(
                "/admin/",
                HttpMethod.GET,
                requestEntity,
                ProblemDetail.class
        );

        System.out.println(response.getBody() + " TEST");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminMe_shouldReturnSuccess_withAdminJwt() throws CodeGenerationException {
        dsl.insertInto(ADMINS)
                .set(ADMINS.EMAIL, "admin2@example.com")
                .set(ADMINS.PASSWORD_HASH, passwordEncoder.encode("password"))
                .set(ADMINS.TOTP_SECRET, "secret")
                .execute();

        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        String code = codeGenerator.generate("secret", Instant.now().getEpochSecond() / 30);

        var loginResponse = restTemplate.postForEntity(
                URI.create("/admin/auth/login"),
                new AdminLoginRequest("admin2@example.com", "password", code),
                AdminAuthResponse.class
        );

        String adminToken = loginResponse.getBody().accessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        var response = restTemplate.exchange(
                "/admin/",
                HttpMethod.GET,
                requestEntity,
                AdminDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().email()).isEqualTo("admin2@example.com");
    }
}
