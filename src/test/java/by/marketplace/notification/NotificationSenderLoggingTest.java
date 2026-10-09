package by.marketplace.notification;

import by.marketplace.auth.dto.Channel;
import by.marketplace.notification.impl.NotificationSenderImpl;
import by.marketplace.notification.service.TemplateEngineImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class NotificationSenderLoggingTest {

    private NotificationSenderImpl notificationSender;

    @BeforeEach
    void setUp() {
        notificationSender = new NotificationSenderImpl(
                Mockito.mock(UniSenderClient.class), Mockito.mock(TemplateEngineImpl.class));
    }

    @Test
    void send_doesNotLogOtpCodeOrFullPhone(CapturedOutput output) {
        notificationSender.send(Channel.SMS, "+375291234567", "654321");

        assertThat(output)
                .doesNotContain("654321")
                .doesNotContain("291234567");
    }

    @Test
    void sendOtpAsync_doesNotLogOtpCode(CapturedOutput output) {
        notificationSender.sendOtpAsync(1L, Channel.SMS, "+375291234567", "654321");

        assertThat(output)
                .doesNotContain("654321")
                .doesNotContain("291234567");
    }

    @Test
    void notify_doesNotLogMessageText(CapturedOutput output) {
        notificationSender.notify(Channel.EMAIL, "ivan.petrov@mail.by",
                "Ссылка: /reports/view?token=abc-secret-token");

        assertThat(output)
                .doesNotContain("abc-secret-token")
                .doesNotContain("ivan.petrov@mail.by")
                .doesNotContain("/reports/view");
    }
}