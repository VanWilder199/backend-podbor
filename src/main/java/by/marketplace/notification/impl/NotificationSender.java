package by.marketplace.notification.impl;

import by.marketplace.auth.dto.Channel;
import by.marketplace.shared.logging.LogMasks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSender implements by.marketplace.notification.NotificationSender {

    /**
     * Асинхронная отправка OTP уведомления.
     * Вызывается из background-потока после коммита основной транзакции.
     */
    @Override
    @Async("otpTaskExecutor")
    public void sendOtpAsync(Long otpId, Channel channel, String destination, String code) {
        try {
            log.info("Sending OTP notification: id={}, destination={}, channel={}",
                    otpId, LogMasks.destination(destination), channel);

            send(channel, destination, code);

            log.info("OTP notification sent successfully: id={}", otpId);

        } catch (Exception e) {
            log.error("Failed to send OTP notification: id={}", otpId, e);
        }
    }

    @Override
    public void send(Channel channel, String destination, String code) {
        // OTP-код и полный destination в лог не пишем никогда.
        // Детальная строка с id/destination пишется выше, в sendOtpAsync.
        // TODO: Интеграция с реальным SMS/Email провайдером
    }

    @Override
    public void notify(Channel channel, String destination, String message) {
        // Текст сообщения (в будущем — со ссылкой и сырым токеном) в лог не пишем.
        log.info("Notification queued: channel={}, destination={}, length={}",
                channel, LogMasks.destination(destination), message.length());
        // TODO: Интеграция с реальным SMS/Email провайдером
    }
}