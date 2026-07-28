package by.marketplace.auth;

import by.marketplace.auth.dto.Channel;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;

import static by.marketplace.jooq.tables.OtpRateLimits.OTP_RATE_LIMITS;

@Component
@RequiredArgsConstructor
public class OtpRateLimiter {
    private final Logger logger = LoggerFactory.getLogger(OtpRateLimiter.class);

    private final DSLContext dsl;

    private static final int MAX_REQUESTS = 3;
    private static final int WINDOW_SECONDS = 3600;


    @Transactional
    public void tryConsumer(Channel channel,String destination) {
        OffsetDateTime now = OffsetDateTime.now();


       var record =  dsl
                .selectFrom(OTP_RATE_LIMITS)
                .where(OTP_RATE_LIMITS.DESTINATION.eq(destination))
                .forUpdate()
                .fetchOne();

       if (record == null) {
                      logger.info("Creating new rate limit record for destination={}", destination);

                      createLimit(now, destination, channel);
                      return;

       }

        OffsetDateTime windowStart = record.getWindowStart();
        int sendCount = record.getSendCount();


        if (windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
            logger.info("Resetting rate limit for destination={}", destination);

            this.resetLimit(now, destination);
            return;
        }

        if (sendCount >= MAX_REQUESTS) {
            logger.info("Rate limit exceeded for destination={}", destination);
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
