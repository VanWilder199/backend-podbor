package by.marketplace.shared.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    OTP_EXPIRED(
            "OTP_EXPIRED",
            "OTP code has expired",
            HttpStatus.BAD_REQUEST
    ),
    OTP_INVALID(
            "OTP_INVALID",
            "Invalid OTP code",
            HttpStatus.BAD_REQUEST
    ),
    OTP_SEND_FAILED(
            "OTP_SEND_FAILED",
            "Failed to send OTP code",
            HttpStatus.INTERNAL_SERVER_ERROR
    ),
    OTP_RATE_LIMIT_EXCEEDED(
            "OTP_RATE_LIMIT_EXCEEDED",
            "Too many OTP requests. Try again later",
            HttpStatus.TOO_MANY_REQUESTS
    ),
    USER_NOT_FOUND(
            "USER_NOT_FOUND",
            "User not found",
            HttpStatus.NOT_FOUND
    ),
    INVALID_REFRESH_TOKEN(
            "INVALID_REFRESH_TOKEN",
            "Invalid or expired refresh token",
            HttpStatus.UNAUTHORIZED
    ),
    REFRESH_TOKEN_REVOKED(
            "REFRESH_TOKEN_REVOKED",
            "Refresh token has been revoked",
            HttpStatus.UNAUTHORIZED
    ),
    INTERNAL_SERVER_ERROR(
            "INTERNAL_SERVER_ERROR",
            "Internal server error",
            HttpStatus.INTERNAL_SERVER_ERROR
    ),
    BAD_REQUEST(
            "BAD_REQUEST",
            "Invalid request",
            HttpStatus.BAD_REQUEST
    ),
    UNAUTHORIZED(
            "UNAUTHORIZED",
            "Unauthorized",
            HttpStatus.UNAUTHORIZED
    ),
    FORBIDDEN(
            "FORBIDDEN",
            "Forbidden",
            HttpStatus.FORBIDDEN
    ),
    NOT_FOUND(
            "NOT_FOUND",
            "Resource not found",
            HttpStatus.NOT_FOUND
    ),
    TELEGRAM_AUTH_INVALID(
            "TELEGRAM_AUTH_INVALID",
            "Invalid Telegram authentication",
            HttpStatus.UNAUTHORIZED
    ),
    TELEGRAM_AUTH_EXPIRED(
            "TELEGRAM_AUTH_EXPIRED",
            "Telegram authentication has expired",
            HttpStatus.UNAUTHORIZED
    ),
    INSPECTOR_NOT_FOUND(
            "INSPECTOR_NOT_FOUND",
            "Inspector not found",
            HttpStatus.NOT_FOUND
    ),
    INSPECTOR_ALREADY_REGISTERED(
            "INSPECTOR_ALREADY_REGISTERED",
            "Inspector already registered",
            HttpStatus.CONFLICT
    ),
    CAR_LISTING_NOT_FOUND(
            "CAR_LISTING_NOT_FOUND",
            "Car listing not found on av.by",
            HttpStatus.NOT_FOUND
    ),
    PARSER_ERROR(
            "PARSER_ERROR",
            "Failed to parse car listing",
            HttpStatus.BAD_GATEWAY
    ),
    CAR_NOT_FOUND(
            "CAR_NOT_FOUND",
            "Car not found",
            HttpStatus.NOT_FOUND
    ),
    NOT_ALLOWDED_MEDIA_TYPE(
            "NOT_ALLOWDED_MEDIA_TYPE",
            "Not allowed media type",
            HttpStatus.BAD_REQUEST
    ),
    MEDIA_NOT_FOUND(
            "MEDIA_NOT_FOUND",
            "Media not found",
            HttpStatus.NOT_FOUND
    ),
    UNEXPECTED_ERROR(
            "UNEXPECTED_ERROR",
            "Unexpected error",
            HttpStatus.INTERNAL_SERVER_ERROR
    ),
    REPORT_NOT_FOUND(
            "REPORT_NOT_FOUND",
            "Report not found",
            HttpStatus.NOT_FOUND
    ),
    SECTION_NOT_FOUND(
            "SECTION_NOT_FOUND",
            "Section not found",
            HttpStatus.NOT_FOUND
    ),
    PANEL_NOT_FOUND(
            "PANEL_NOT_FOUND",
            "Panel not found",
            HttpStatus.NOT_FOUND
    ),
    REPORT_ACCESS_DENIED(
            "REPORT_ACCESS_DENIED",
            "Access to report denied",
            HttpStatus.FORBIDDEN
    ),
    REPORT_NOT_EDITABLE(
            "REPORT_NOT_EDITABLE",
            "Report is not editable",
            HttpStatus.CONFLICT
    ),
    REPORT_INCOMPLETE(
            "REPORT_INCOMPLETE",
            "Report is not complete",
            HttpStatus.CONFLICT
    );



    private final String code;
    private final String title;
    private final HttpStatus status;
}
