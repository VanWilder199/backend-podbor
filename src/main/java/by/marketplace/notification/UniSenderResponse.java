package by.marketplace.notification;

import java.util.List;

public record UniSenderResponse(
        List<UniSenderResultItem> result,
        String error,
        String code
) {
    public record UniSenderResultItem(
            Integer index,
            String email,
            String id,
            List<UniSenderError> errors
    ) {}

    public record UniSenderError(
            String code,
            String message
    ) {}
}
