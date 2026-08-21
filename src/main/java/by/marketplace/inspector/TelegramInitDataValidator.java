package by.marketplace.inspector;

import by.marketplace.config.TelegramProperties;
import by.marketplace.inspector.dto.TelegramUserPayload;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TelegramInitDataValidator
{
    private static final String HMAC_KEY_SEED = "WebAppData";

    private final TelegramProperties telegramProperties;
    private final ObjectMapper objectMapper;
    private final byte[] secretKey;

    public TelegramInitDataValidator(TelegramProperties telegramProperties, ObjectMapper objectMapper) {
        this.telegramProperties = telegramProperties;
        this.objectMapper = objectMapper;
        this.secretKey = hmacSha256(
                HMAC_KEY_SEED.getBytes(StandardCharsets.UTF_8),
                telegramProperties.botToken().getBytes(StandardCharsets.UTF_8)
        );
    }

    public TelegramUser validate(String initData) {
        Map<String, String> params = parseQueryString(initData);
        verifyHash(params);
        verifyNotExpired(params);
        return extractUser(params);
    }

    private void verifyHash(Map<String, String> params) {
        String receivedHash = params.remove("hash");
        if (receivedHash == null) {
            throw new AppException(ErrorCode.TELEGRAM_AUTH_INVALID);
        }

        String dataCheckString = buildDataCheckString(params);
        byte[] computedHash = hmacSha256(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));

        byte[] expectedHash;

        try {
            expectedHash = HexFormat.of().parseHex(receivedHash);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.TELEGRAM_AUTH_INVALID);
        }

        if (!MessageDigest.isEqual(computedHash, expectedHash)) {
            throw new AppException(ErrorCode.TELEGRAM_AUTH_INVALID);
        }
    }

    private String buildDataCheckString(Map<String, String> params) {
        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    private void verifyNotExpired(Map<String, String> params) {
        long authDate;
        try {
            authDate = Long.parseLong(params.get("auth_date"));
        } catch (NumberFormatException | NullPointerException e) {
            throw new AppException(ErrorCode.TELEGRAM_AUTH_INVALID);
        }

        Instant expiresAt = Instant.ofEpochSecond(authDate).plus(telegramProperties.maxAge());
        if (expiresAt.isBefore(Instant.now())) {
            throw new AppException(ErrorCode.TELEGRAM_AUTH_EXPIRED);
        }
    }

    private TelegramUser extractUser(Map<String, String> params) {
        String userJson = params.get("user");
        if (userJson == null) {
            throw new AppException(ErrorCode.TELEGRAM_AUTH_INVALID);
        }

        TelegramUserPayload payload;
        try {
            payload = objectMapper.readValue(userJson, TelegramUserPayload.class);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.TELEGRAM_AUTH_INVALID);
        }

        return new TelegramUser(payload.id(), payload.firstName(), payload.username());
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> map = new HashMap<>();

        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx < 0) {
                continue;
            }
            String key = pair.substring(0, idx);

            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);

            map.put(key, value);
        }
        return map;
    }
}
