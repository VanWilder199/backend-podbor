package by.marketplace.notification;

import by.marketplace.config.UnisenderProperties;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;


@Slf4j
@Component
public class UniSenderClient {
    private final RestClient restClient;
    private final UnisenderProperties props;

    public UniSenderClient(@Qualifier("unisenderRestClient") RestClient restClient, UnisenderProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    public void sendSms(String phone, String text) {
        String uri = "/sendSms?format=json&api_key={apiKey}&phone={phone}&sender={sender}&text={text}";

        UniSenderSmsResponse response = execute(() -> restClient.get()
                .uri(uri, Map.of("phone", phone, "sender", props.smsSender(), "text", text))
                .retrieve().body(UniSenderSmsResponse.class));
        checkApiError(response.error(), response.code(), "sendSms");
    }

    public void sendEmail(String to, String subject, String htmlBody) {
        String uri = "/sendEmail?format=json&api_key={apiKey}&email={email}&sender_name={senderName}" +
                "&sender_email={senderEmail}&subject={subject}&body={body}&list_id={listId}&error_checking=1";

        UniSenderResponse response = execute(() -> restClient.get()
                .uri(uri, Map.of(
                        "email", to,
                        "senderName", props.senderName(),
                        "senderEmail", props.senderEmail(),
                        "subject", subject,
                        "body", htmlBody,
                        "listId", props.emailListId()
                ))
                .retrieve().body(UniSenderResponse.class));
        checkApiError(response.error(), response.code(), "sendEmail");
        checkResultErrors(response, to);
    }

    private <T> T execute(Supplier<T> call) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return call.get();
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().is5xxServerError() && attempt < 3) { backoff(attempt); continue; }
                throw new AppException(ErrorCode.NOTIFICATION_SEND_FAILED, "unisender request failed", e);
            } catch (RestClientException e) {
                if (attempt < 3) { backoff(attempt); continue; }
                throw new AppException(ErrorCode.NOTIFICATION_SEND_FAILED, "unisender request failed", e);
            }
        }
        throw new AppException(ErrorCode.NOTIFICATION_SEND_FAILED, "unisender request failed");
    }

    private void checkApiError(String error, String code, String op) {
        if (error != null) {
            log.error("unisender {} API error: code={}, message={}", op, code, error);
            throw new AppException(ErrorCode.NOTIFICATION_SEND_FAILED, error);
        }
    }

    private void checkResultErrors(UniSenderResponse r, String to) {
        if (r.result() == null) return;

        r.result().stream()
                .flatMap(item -> item.errors() == null ? Stream.empty() : item.errors().stream())
                .forEach(err -> log.error("unisender sendEmail error for {}: code={}, message={}",
                        to, err.code(), err.message()));
        if (r.result().stream().anyMatch(item -> item.errors() != null && !item.errors().isEmpty())) {
            throw new AppException(ErrorCode.NOTIFICATION_SEND_FAILED, "unisender sendEmail failed");
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(500L * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
