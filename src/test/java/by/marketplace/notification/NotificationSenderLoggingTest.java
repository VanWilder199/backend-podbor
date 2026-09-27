package by.marketplace.notification;

import by.marketplace.auth.dto.Channel;
import by.marketplace.notification.impl.NotificationSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class NotificationSenderLoggingTest {

    @Test
    void send_doesNotLogOtpCodeOrFullPhone(CapturedOutput output) {
        new NotificationSender().send(Channel.SMS, "+375291234567", "654321");

        assertThat(output)
                .doesNotContain("654321")
                .doesNotContain("291234567");
    }

    @Test
    void sendOtpAsync_doesNotLogOtpCode(CapturedOutput output) {
        new NotificationSender().sendOtpAsync(1L, Channel.SMS, "+375291234567", "654321");

        assertThat(output)
                .doesNotContain("654321")
                .doesNotContain("291234567");
    }

    @Test
    void notify_doesNotLogMessageText(CapturedOutput output) {
        new NotificationSender().notify(Channel.EMAIL, "ivan.petrov@mail.by",
                "Ссылка: /reports/view?token=abc-secret-token");

        assertThat(output)
                .doesNotContain("abc-secret-token")
                .doesNotContain("ivan.petrov@mail.by")
                .doesNotContain("/reports/view");
    }
}