package by.marketplace.shared.exception;

import by.marketplace.shared.logging.RequestLoggingFilter;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

public final class ProblemDetails {

    public static final String TRACE_ID_KEY = RequestLoggingFilter.TRACE_ID;

    private ProblemDetails() {
    }

    public static ProblemDetail withTraceId(ProblemDetail pd) {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId != null) {
            pd.setProperty(TRACE_ID_KEY, traceId);
        }
        return pd;
    }
}