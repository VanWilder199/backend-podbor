package by.marketplace.utils;

import by.marketplace.inspector.dto.TelegramUserPayload;
import com.fasterxml.jackson.databind.ObjectMapper;

import static by.marketplace.inspector.TelegramTestDataGenerator.bytesToHex;
import static by.marketplace.inspector.TelegramTestDataGenerator.hmacSha256;


public class InspectorUtils {
    public static final long USER_ID = 123456789L;
    public static final String FIRST_NAME = "Test";
    public static final String USERNAME = "testuser";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String HMAC_KEY_SEED = "WebAppData";
    private static final String BOT_TOKEN = "test-token";


    public static String buildValidInitData(long authDate) throws Exception {
        String userJson = objectMapper.writeValueAsString(
                new TelegramUserPayload(USER_ID, FIRST_NAME, USERNAME)
        );

        String dataCheckString = "auth_date=" + authDate + "\nuser=" + userJson;

        byte[] secretKey = hmacSha256(HMAC_KEY_SEED.getBytes(), BOT_TOKEN.getBytes());


        String hash = bytesToHex(hmacSha256(secretKey, dataCheckString.getBytes()));

        return  "auth_date=" + authDate + "&user=" + userJson + "&hash=" + hash;


    }
}
