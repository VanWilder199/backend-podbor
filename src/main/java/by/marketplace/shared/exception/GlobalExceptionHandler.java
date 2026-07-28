package by.marketplace.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleAppException(
            AppException ex,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ex.getErrorCode();

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(),
                ex.getMessage()
        );

        problemDetail.setType(URI.create("/errors/" + errorCode.getCode().toLowerCase()));
        problemDetail.setTitle(errorCode.getTitle());
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", request.getRequestURI());
        problemDetail.setProperty("errorCode", errorCode.getCode());

        log.warn(
                "AppException: {} at {} (errorCode={})",
                ex.getMessage(),
                request.getRequestURI(),
                errorCode.getCode()
        );

        return problemDetail;

    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unexpected exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );

        problemDetail.setType(URI.create("/errors/internal-server-error"));
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", request.getRequestURI());
        problemDetail.setProperty("errorCode", ErrorCode.INTERNAL_SERVER_ERROR.getCode());

        return problemDetail;

    }
}
