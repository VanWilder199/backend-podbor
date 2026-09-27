package by.marketplace.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleAppException(
            AppException ex,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = errorCode.getStatus();

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                status,
                ex.getMessage()
        );

        problemDetail.setType(URI.create("/errors/" + errorCode.getCode().toLowerCase()));
        problemDetail.setTitle(errorCode.getTitle());
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", request.getRequestURI());
        problemDetail.setProperty("errorCode", errorCode.getCode());

        if (status.is5xxServerError()) {
            if (ex.getCause() != null) {
                log.error("AppException at {} (errorCode={})",
                        request.getRequestURI(), errorCode.getCode(), ex);
            } else {
                log.error("AppException: {} at {} (errorCode={})",
                        ex.getMessage(), request.getRequestURI(), errorCode.getCode());
            }
        } else if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            log.warn("AppException: {} at {} (errorCode={})",
                    ex.getMessage(), request.getRequestURI(), errorCode.getCode());
        } else {
            log.info("AppException: {} at {} (errorCode={})",
                    ex.getMessage(), request.getRequestURI(), errorCode.getCode());
        }

        return ProblemDetails.withTraceId(problemDetail);
    }
}
