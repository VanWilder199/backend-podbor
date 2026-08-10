package by.marketplace.notification.impl;

import by.marketplace.auth.dto.Channel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationSender implements by.marketplace.notification.NotificationSender {
    private final Logger logger = LoggerFactory.getLogger(NotificationSender.class);

    /**
     * Асинхронная отправка OTP уведомления.
     * Вызывается из background-потока после коммита основной транзакции.
     */
    @Override
    @Async("otpTaskExecutor")
    public void sendOtpAsync(Long otpId, Channel channel, String destination, String code) {
        try {
            logger.info("Sending OTP notification: id={}, destination={}, channel={}", otpId, destination, channel);

            send(channel, destination, code);

            logger.info("OTP notification sent successfully: id={}", otpId);

        } catch (Exception e) {
            logger.error("Failed to send OTP notification: id={}", otpId, e);
        }
    }

    @Override
    public void send(Channel channel, String destination, String code) {
        logger.info(">>> Sending OTP to {} via {}: {}", destination, channel, code);
        // TODO: Интеграция с реальным SMS/Email провайдером
    }
}
