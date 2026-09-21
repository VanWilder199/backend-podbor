package by.marketplace;

import by.marketplace.auth.dto.Channel;
import by.marketplace.notification.NotificationSender;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Тестовая реализация NotificationSender.
 * Сохраняет последний отправленный код для проверки в тестах.
 */
@Component
@Primary
public class TestNotificationSender implements NotificationSender {

    private String lastCode;
    private final List<String> messages = new ArrayList<>();

    public String getLastCode() {
        return lastCode;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void clearMessages() {
        messages.clear();
    }

    @Override
    public void send(Channel channel, String destination, String code) {
        this.lastCode = code;
    }

    @Override
    public void sendOtpAsync(Long otpId, Channel channel, String destination, String code) {
        send(channel, destination, code);
    }

    @Override
    public void notify(Channel channel, String destination, String message) {
        messages.add(destination + ": " + message);
    }
}
