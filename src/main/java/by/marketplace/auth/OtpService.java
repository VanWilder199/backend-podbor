package by.marketplace.auth;

import by.marketplace.auth.dto.AuthResponse;
import by.marketplace.auth.dto.Channel;
import by.marketplace.auth.service.JwtService;
import by.marketplace.jooq.tables.records.OtpCodesRecord;
import by.marketplace.notification.NotificationSender;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import by.marketplace.shared.logging.LogMasks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

import static by.marketplace.jooq.Tables.OTP_CODES;
import static by.marketplace.jooq.Tables.USERS;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {
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
                .set(OTP_CODES.CODE, passwordEncoder.encode(code))
                .set(OTP_CODES.EXPIRES_AT, OffsetDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .returning(OTP_CODES.ID)
                .fetchOne()
                .getId();

        log.info("OTP created: id={}, destination={}, channel={}", otpId, LogMasks.destination(destination), channel);

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
            log.info("OTP verify: no active code, channel={}, destination={}",
                    channel, LogMasks.destination(destination));
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            log.warn("OTP verify: attempts exhausted, otpId={}, destination={}",
                    otp.getId(), LogMasks.destination(destination));
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (!passwordEncoder.matches(code, otp.getCode())) {
            log.warn("OTP verify: invalid code, otpId={}, attempt={}, max={}",
                    otp.getId(), otp.getAttempts() + 1, MAX_ATTEMPTS);

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

        log.info("Buyer authenticated: userId={}, channel={}", userId, channel);

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
