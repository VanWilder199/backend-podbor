package by.marketplace;

import by.marketplace.auth.OtpRateLimiter;
import by.marketplace.auth.dto.Channel;
import by.marketplace.jooq.tables.records.OtpRateLimitsRecord;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.jetbrains.annotations.Nullable;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;

import static by.marketplace.jooq.Tables.OTP_RATE_LIMITS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpRateLimitTest extends AbstractIntegrationTest {

    private final OtpRateLimiter otpRateLimiter;
    private final DSLContext dsl;

    @Autowired
    OtpRateLimitTest(OtpRateLimiter otpRateLimiter, DSLContext dsl) {
        this.otpRateLimiter = otpRateLimiter;
        this.dsl = dsl;
    }

    @BeforeEach
    void setUp() {
        dsl.deleteFrom(OTP_RATE_LIMITS).execute();
    }

    @Test
    void shouldCreateNewRecordOnFirstRequest() {
        otpRateLimiter.tryConsumer(Channel.SMS, "1234567890");

        @Nullable OtpRateLimitsRecord record = dsl.selectFrom(OTP_RATE_LIMITS)
                .where(OTP_RATE_LIMITS.DESTINATION.eq("1234567890"))
                .fetchOne();

        assertNotNull(record, "Record should be created for the first request");
        assertEquals(1, record.getSendCount(), "Send count should be 1");
    }

    @Test
    void shouldIncrementSendCountOnSecondRequestWithinWindow() {
        otpRateLimiter.tryConsumer(Channel.SMS, "1234567890");
        otpRateLimiter.tryConsumer(Channel.SMS, "1234267890");

        List<OtpRateLimitsRecord> records = dsl.selectFrom(OTP_RATE_LIMITS)
                .fetch();

        assertNotNull(records, "Record should be created for the first request");
        assertEquals(2, records.size(),"Records should be created");
    }

    @Test
    void shouldThrowWhenLimitExceededWithinWindow() {
        String destination = "1234567890";
        otpRateLimiter.tryConsumer(Channel.SMS, destination);
        otpRateLimiter.tryConsumer(Channel.SMS, destination);
        otpRateLimiter.tryConsumer(Channel.SMS, destination);

        AppException exception = assertThrows(AppException.class, () -> {
            otpRateLimiter.tryConsumer(Channel.SMS, destination);
        });

        assertEquals(ErrorCode.OTP_RATE_LIMIT_EXCEEDED, exception.getErrorCode());

        OtpRateLimitsRecord record = dsl.selectFrom(OTP_RATE_LIMITS)
                .where(OTP_RATE_LIMITS.DESTINATION.eq(destination))
                .fetchOne();

        assertNotNull(record, "Record should exist");
        assertEquals(3, record.getSendCount(), "Send count must not grow past the limit");
    }

    @Test
    void shouldResetCountWhenWindowExpired() {
        String destination = "1234567890";
        OffsetDateTime expiredWindowStart = OffsetDateTime.now().minusSeconds(3600 + 1);

        dsl.insertInto(OTP_RATE_LIMITS)
                .set(OTP_RATE_LIMITS.DESTINATION, destination)
                .set(OTP_RATE_LIMITS.CHANNEL, Channel.SMS.toString())
                .set(OTP_RATE_LIMITS.SEND_COUNT, 3)
                .set(OTP_RATE_LIMITS.WINDOW_START, expiredWindowStart)
                .set(OTP_RATE_LIMITS.BLOCKED_UNTIL, OffsetDateTime.now())
                .execute();

        otpRateLimiter.tryConsumer(Channel.SMS, destination);

        OtpRateLimitsRecord record = dsl.selectFrom(OTP_RATE_LIMITS)
                .where(OTP_RATE_LIMITS.DESTINATION.eq(destination))
                .fetchOne();

        assertNotNull(record, "Record should exist");
        assertEquals(1, record.getSendCount(), "Send count should reset to 1");
        assertTrue(record.getWindowStart().isAfter(expiredWindowStart), "Window start should be updated to now");
        assertNull(record.getBlockedUntil(), "Blocked until should be cleared");
    }
}
