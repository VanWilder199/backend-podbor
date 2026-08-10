package by.marketplace;

import by.marketplace.auth.dto.Channel;
import by.marketplace.notification.NotificationSender;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Тестовая реализация NotificationSender.
 * Сохраняет последний отправленный код для проверки в тестах.
 */
@Component
@Primary
public class TestNotificationSender implements NotificationSender {
    
    private String lastCode;

    public String getLastCode() {
        return lastCode;
    }

    @Override
    public void send(Channel channel, String destination, String code) {
        this.lastCode = code;
    }

    @Override
    public void sendOtpAsync(Long otpId, Channel channel, String destination, String code) {
        send(channel, destination, code);
    }
}
