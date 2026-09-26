package by.marketplace.utils;

import by.marketplace.auth.dto.AdminAuthResponse;
import by.marketplace.auth.dto.AdminLoginRequest;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public final class AdminTestUtils {

    private AdminTestUtils() {
    }

    /** Логин админа через /admin/auth/login (TOTP) и возврат Bearer-заголовков. */
    public static HttpHeaders adminHeaders(
            TestRestTemplate restTemplate,
            String email,
            String password,
            String totpSecret
    ) throws CodeGenerationException {
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        String code = codeGenerator.generate(totpSecret, Instant.now().getEpochSecond() / 30);

        ResponseEntity<AdminAuthResponse> login = restTemplate.postForEntity(
                URI.create("/admin/auth/login"),
                new AdminLoginRequest(email, password, code),
                AdminAuthResponse.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().accessToken());
        return headers;
    }
}