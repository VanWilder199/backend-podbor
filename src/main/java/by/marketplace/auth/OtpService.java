package by.marketplace.auth;

import by.marketplace.auth.dto.Channel;
import by.marketplace.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

import static by.marketplace.jooq.Tables.OTP_CODES;

@Service
@RequiredArgsConstructor
public class OtpService {
    private final Logger logger = LoggerFactory.getLogger(OtpService.class);

    private final DSLContext dsl;
    private final OtpRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    private static final int OTP_TTL_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public void sendOtp(Channel channel, String destination) {
        rateLimiter.tryConsumer(channel, destination);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);

        dsl.insertInto(OTP_CODES)
                .set(OTP_CODES.CHANNEL, channel.toString())
                .set(OTP_CODES.DESTINATION, destination)
                .set(OTP_CODES.CODE_HASH, codeHash)
                .set(OTP_CODES.EXPIRES_AT, OffsetDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .execute();


        notificationService.sendOtp(channel, destination, code);
        logger.info("OTP sent to destination={} via {}", destination, channel);


    }
//    id BIGSERIAL PRIMARY KEY,
//    channel TEXT NOT NULL,
//    destination TEXT NOT NULL,
//    code_hash TEXT NOT NULL,
//    attempts INT NOT NULL DEFAULT 0,
//    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
//    consumed_at TIMESTAMP WITH TIME ZONE
}
