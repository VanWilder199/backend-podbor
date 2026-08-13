package by.marketplace.inspector;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Утилита для генерации валидных Telegram init data для локальных тестов.
 *
 * <p>Используется для генерации корректного HMAC-SHA256 hash, который принимает
 * {@link TelegramInitDataValidator}. Без этого запросы на /inspector/register
 * будут отклоняться с 403 Forbidden.</p>
 *
 * <p><b>Как использовать:</b></p>
 * <ol>
 *   <li>Запустить main() — выведет auth_date, user и hash</li>
 *   <li>Скопировать вывод в curl-запрос</li>
 *   <li>Или использовать в тестах для программной генерации</li>
 * </ol>
 *
 * <p><b>Пример curl:</b></p>
 * <pre>{@code
 * curl -X POST "http://localhost:8080/inspector/register" \
 *   -H "Content-Type: application/json" \
 *   -H "X-Telegram-Data: auth_date=1723564800&user=...&hash=..." \
 *   -d '{"fullName":"Иванов Иван","phone":"+79991234567","email":"ivan@test.com"}'
 * }</pre>
 *
 * <p><b>Важно:</b> Bot token должен совпадать с application-local.yml</p>
 */
public class TelegramTestDataGenerator {

    private static final String BOT_TOKEN = "1234567890:AAFgH8K9L0mNpQrStUvWxYz";
    private static final String HMAC_KEY_SEED = "WebAppData";

    public static void main(String[] args) throws Exception {
        long authDate = System.currentTimeMillis() / 1000;
        String userJson = "{\"id\":123456789,\"first_name\":\"Test\",\"username\":\"testuser\"}";
        String userEncoded = URLEncoder.encode(userJson, StandardCharsets.UTF_8);

        byte[] secretKey = hmacSha256(HMAC_KEY_SEED.getBytes(), BOT_TOKEN.getBytes());
        // Валидатор декодирует параметры, поэтому хешируем декодированное значение
        // auth_date < user в алфавитном порядке
        String dataCheckString = "auth_date=" + authDate + "\nuser=" + userJson;
        String hash = bytesToHex(hmacSha256(secretKey, dataCheckString.getBytes()));

        System.out.println("=== Telegram Init Data для локальных тестов ===\n");
        System.out.println("auth_date=" + authDate);
        System.out.println("user=" + userEncoded);
        System.out.println("hash=" + hash);
        System.out.println("\nПолная строка для заголовка:");
        System.out.println("auth_date=" + authDate + "&user=" + userEncoded + "&hash=" + hash);
        System.out.println("\nПример curl:");
        System.out.println("curl -X POST \"http://localhost:8080/inspector/register\" \\");
        System.out.println("  -H \"Content-Type: application/json\" \\");
        System.out.println("  -H \"X-Telegram-Data: auth_date=" + authDate + "&user=" + userEncoded + "&hash=" + hash + "\" \\");
        System.out.println("  -d '{\"fullName\":\"Иванов Иванов\",\"phone\":\"+79991234567\",\"email\":\"ivan@test.com\"}'");
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(message);
    }

    private static String bytesToHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
