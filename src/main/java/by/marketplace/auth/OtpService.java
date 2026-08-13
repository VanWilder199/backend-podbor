package by.marketplace.auth;

import by.marketplace.auth.dto.AuthResponse;
import by.marketplace.auth.dto.Channel;
import by.marketplace.jooq.tables.records.OtpCodesRecord;
import by.marketplace.notification.NotificationSender;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

import static by.marketplace.jooq.Tables.OTP_CODES;
import static by.marketplace.jooq.Tables.USERS;

@Service
@RequiredArgsConstructor
public class OtpService {
    private final Logger logger = LoggerFactory.getLogger(OtpService.class);

    private final DSLContext dsl;
    private final OtpRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final NotificationSender notificationService;
    private final JwtService jwtService;

    private static final int OTP_TTL_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public void sendOtp(Channel channel, String destination) {
        rateLimiter.tryConsumer(channel, destination);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        Long otpId = dsl.insertInto(OTP_CODES)
                .set(OTP_CODES.CHANNEL, channel.toString())
                .set(OTP_CODES.DESTINATION, destination)
                .set(OTP_CODES.CODE, code)
                .set(OTP_CODES.EXPIRES_AT, OffsetDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .returning(OTP_CODES.ID)
                .fetchOne()
                .getId();

        logger.info("OTP created: id={}, destination={}, channel={}", otpId, destination, channel);

        notificationService.sendOtpAsync(otpId, channel, destination, code);
    }

    @Transactional(noRollbackFor = AppException.class)
    public AuthResponse verifyOtp(Channel channel, String destination, String code) {
        OtpCodesRecord otp = dsl.selectFrom(OTP_CODES)
                .where(OTP_CODES.DESTINATION.eq(destination))
                .and(OTP_CODES.CONSUMED_AT.isNull())
                .and(OTP_CODES.EXPIRES_AT.gt(OffsetDateTime.now()))
                .limit(1)
                .forUpdate()
                .fetchOne();

        if (otp == null) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (!passwordEncoder.matches(code, otp.getCode())) {
            dsl.update(OTP_CODES)
                    .set(OTP_CODES.ATTEMPTS, otp.getAttempts() + 1)
                    .where(OTP_CODES.ID.eq(otp.getId()))
                    .execute();

            throw new AppException(ErrorCode.OTP_INVALID);
        }

        dsl.update(OTP_CODES)
                .set(OTP_CODES.CONSUMED_AT, OffsetDateTime.now())
                .where(OTP_CODES.ID.eq(otp.getId()))
                .execute();

        UUID userId = upsertByDestination(channel, destination);

        return jwtService.issueTokens(userId, channel == Channel.EMAIL ? destination : null, "BUYER");
    }

    private UUID upsertByDestination(Channel channel, String destination) {
        if (channel == Channel.SMS) {
            return upsertBySms(destination);
        }

        return upsertByEmail(destination);
    }

    private UUID upsertBySms(String destination) {
        return dsl.insertInto(USERS)
                .set(USERS.PHONE, destination)
                .onConflict(USERS.PHONE)
                .doUpdate().set(USERS.PHONE, destination)
                .returning(USERS.ID)
                .fetchOne()
                .getId();
    }

    private UUID upsertByEmail(String destination) {
        return dsl.insertInto(USERS)
                .set(USERS.EMAIL, destination)
                .onConflict(USERS.EMAIL)
                .doUpdate().set(USERS.EMAIL, destination)
                .returning(USERS.ID)
                .fetchOne()
                .getId();
    }
}
