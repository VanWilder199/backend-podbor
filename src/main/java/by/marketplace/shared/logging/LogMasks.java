package by.marketplace.shared.logging;

/**
 * Маскирование чувствительных значений для логов.
 * Утилита никогда не должна бросать исключения и ронять бизнес-код.
 */
public final class LogMasks {

    private LogMasks() {
    }

    /**
     * "+375291234567" -> "+375*****4567".
     * Слишком короткие значения возвращаются как есть.
     */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 8) {
            return phone;
        }
        return phone.substring(0, 4) + "*".repeat(phone.length() - 8) + phone.substring(phone.length() - 4);
    }

    /**
     * "ivan.petrov@mail.by" -> "i***@mail.by".
     */
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * По наличию '@' решает, что маскировать — email или телефон.
     */
    public static String destination(String destination) {
        if (destination == null) {
            return null;
        }
        return destination.contains("@") ? email(destination) : phone(destination);
    }
}