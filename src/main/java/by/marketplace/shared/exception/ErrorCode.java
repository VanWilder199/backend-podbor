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
    );


    private final String code;
    private final String title;
    private final HttpStatus status;
}
