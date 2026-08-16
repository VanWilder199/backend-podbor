package by.marketplace.inspector;

import by.marketplace.config.TelegramProperties;
import by.marketplace.inspector.dto.TelegramUserPayload;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.time.Duration;
import java.time.Instant;

import static by.marketplace.inspector.TelegramTestDataGenerator.bytesToHex;
import static by.marketplace.inspector.TelegramTestDataGenerator.hmacSha256;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowableOfType;

public class TelegramInitDataValidatorTest {

    private static final long USER_ID = 123456789L;
    private static final String FIRST_NAME = "Test";
    private static final String USERNAME = "testuser";


    private static final String BOT_TOKEN = "test-token";
    private static final String HMAC_KEY_SEED = "WebAppData";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TelegramInitDataValidator telegramInitDataValidator;

    @BeforeEach
    void setUp() {
        TelegramProperties telegramProperties = new TelegramProperties(BOT_TOKEN, Duration.ofHours(1));
        telegramInitDataValidator = new TelegramInitDataValidator(telegramProperties, new ObjectMapper());
    }

    @Test
    void validSignature_returnsUser() throws Exception {
        String  initData = buildValidInitData(System.currentTimeMillis() / 1000);

        TelegramUser user = telegramInitDataValidator.validate(initData);

        assertThat(user.id()).isEqualTo(USER_ID);
        assertThat(user.firstName()).isEqualTo(FIRST_NAME);
        assertThat(user.username()).isEqualTo(USERNAME);
    }

    @Test
    void expiredAuthDate_throwsExpired() throws Exception {
        long twoHoursAgo = Instant.now().minus(Duration.ofHours(2)).getEpochSecond();
        String initData = buildValidInitData(twoHoursAgo);

        AppException exception = catchThrowableOfType(() -> telegramInitDataValidator.validate(initData), AppException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TELEGRAM_AUTH_EXPIRED);

    }

    @Test
    void invalidSignature_throwsInvalid() throws Exception {
        String validInitData = buildValidInitData(System.currentTimeMillis() / 1000);
        String invalidInitData = validInitData.replace("hash=", "hash=invalid");

        AppException exception = catchThrowableOfType(() -> telegramInitDataValidator.validate(invalidInitData), AppException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TELEGRAM_AUTH_INVALID);
    }

    private String buildValidInitData(long authDate) throws Exception {
        String userJson = objectMapper.writeValueAsString(
                new TelegramUserPayload(USER_ID, FIRST_NAME, USERNAME)
        );

        String dataCheckString = "auth_date=" + authDate + "\nuser=" + userJson;

        byte[] secretKey = hmacSha256(HMAC_KEY_SEED.getBytes(), BOT_TOKEN.getBytes());


        String hash = bytesToHex(hmacSha256(secretKey, dataCheckString.getBytes()));

        return  "auth_date=" + authDate + "&user=" + userJson + "&hash=" + hash;


    }

}
