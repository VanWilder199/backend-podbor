package by.marketplace.auth;

import by.marketplace.auth.dto.Channel;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import by.marketplace.shared.logging.LogMasks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static by.marketplace.jooq.tables.OtpRateLimits.OTP_RATE_LIMITS;

@Slf4j
@Component
@RequiredArgsConstructor
public class OtpRateLimiter {
    private final DSLContext dsl;

    private static final int MAX_REQUESTS = 3;
    private static final int WINDOW_SECONDS = 3600;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tryConsumer(Channel channel,String destination) {
        OffsetDateTime now = OffsetDateTime.now();


       var record =  dsl
                .selectFrom(OTP_RATE_LIMITS)
                .where(OTP_RATE_LIMITS.DESTINATION.eq(destination))
                .forUpdate()
                .fetchOne();

       if (record == null) {
                      log.debug("Creating new rate limit record for destination={}", LogMasks.destination(destination));

                      createLimit(now, destination, channel);
                      return;

       }

        OffsetDateTime windowStart = record.getWindowStart();
        int sendCount = record.getSendCount();


        if (windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
            log.debug("Resetting rate limit for destination={}", LogMasks.destination(destination));

            this.resetLimit(now, destination);
            return;
        }

        if (sendCount >= MAX_REQUESTS) {
            log.warn("OTP rate limit exceeded: channel={}, destination={}, sendCount={}",
                    channel, LogMasks.destination(destination), sendCount);
            throw new AppException(ErrorCode.OTP_RATE_LIMIT_EXCEEDED);
        }


        this.updateLimit(sendCount, destination);
    }

    private void createLimit(OffsetDateTime now, String destination, Channel channel) {
        dsl.insertInto(OTP_RATE_LIMITS)
                .set(OTP_RATE_LIMITS.DESTINATION, destination)
                .set(OTP_RATE_LIMITS.CHANNEL, channel.toString())
                .set(OTP_RATE_LIMITS.SEND_COUNT, 1)
                .set(OTP_RATE_LIMITS.WINDOW_START, now)
                .execute();
    }

    private void resetLimit(OffsetDateTime now, String destination) {
        dsl.update(OTP_RATE_LIMITS)
                .set(OTP_RATE_LIMITS.SEND_COUNT, 1)
                .set(OTP_RATE_LIMITS.WINDOW_START, now)
                .set(OTP_RATE_LIMITS.BLOCKED_UNTIL, (OffsetDateTime) null)
                .where(OTP_RATE_LIMITS.DESTINATION.eq(destination))
                .execute();
    }

    private void updateLimit(int count, String destination) {
        dsl.update(OTP_RATE_LIMITS)
                .set(OTP_RATE_LIMITS.SEND_COUNT, count + 1)
                .where(OTP_RATE_LIMITS.DESTINATION.eq(destination))
                .execute();
    }
}
