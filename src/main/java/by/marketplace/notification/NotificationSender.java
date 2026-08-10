package by.marketplace.notification;

import by.marketplace.auth.dto.Channel;

/**
 * Интерфейс для отправки OTP уведомлений.
 * Нужен для тестируемости — можно подменить реализацию в тестах.
 */
public interface NotificationSender {

    /**
     * Отправить OTP код пользователю.
     */
    void send(Channel channel, String destination, String code);

    /**
     * Асинхронная отправка OTP уведомления.
     */
    void sendOtpAsync(Long otpId, Channel channel, String destination, String code);
}
