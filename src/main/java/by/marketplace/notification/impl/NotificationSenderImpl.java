package by.marketplace.notification.impl;

import by.marketplace.auth.dto.Channel;
import by.marketplace.notification.NotificationSender;
import by.marketplace.notification.UniSenderClient;
import by.marketplace.notification.service.TemplateEngineImpl;
import by.marketplace.shared.logging.LogMasks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSenderImpl implements NotificationSender {
    private final UniSenderClient uniSenderClient;
    private final TemplateEngineImpl templateEngine;

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
       switch (channel) {
           case SMS -> uniSenderClient.sendSms(destination, code);
           case EMAIL -> uniSenderClient.sendEmail(destination,"Ваш OTP-код" , templateEngine.renderOtpHtml(code));
       }
    }

    @Override
    public void notify(Channel channel, String destination, String message) {
        switch (channel) {
            case SMS -> uniSenderClient.sendSms(destination, message);
            case EMAIL -> uniSenderClient.sendEmail(destination, "Уведомление", templateEngine.renderNotificationHtml(message));
        }
    }
}