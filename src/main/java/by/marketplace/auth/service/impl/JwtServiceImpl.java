package by.marketplace.auth.service.impl;

import by.marketplace.auth.dto.AuthResponse;
import by.marketplace.auth.service.JwtService;
import by.marketplace.config.JwtProperties;
import by.marketplace.jooq.tables.records.RefreshTokensRecord;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

    import io.jsonwebtoken.Claims;
       import io.jsonwebtoken.Jwts;                    // ← отсюда Jwts.builder()
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static by.marketplace.jooq.Tables.*;


@Service
public class JwtServiceImpl implements JwtService {
    private final SecretKey key;
    private final long accessExpiration;
    private final DSLContext dsl;

    private int refreshTokenExpiration = 72;
    private static final SecureRandom RANDOM = new SecureRandom();


   @Override
   public long getAccessTokenExpiration() {
        return accessExpiration;
    }

    public JwtServiceImpl(JwtProperties props, DSLContext dsl) {
        this.key = props.getSecretKey();
        this.accessExpiration = props.getAccessTokenExpiration();
        this.dsl = dsl;
    }


    @Override
    public AuthResponse issueTokens(UUID userId, String email, String role) {
        String access = generateAccessToken(userId, email, role);
        String refresh = refreshToken(userId);

        return new AuthResponse(access, refresh, OffsetDateTime.now().plusHours(refreshTokenExpiration).toEpochSecond());
    }

    @Override
    public String generateAccessToken(UUID userId, String email, String role) {
        return generateAccessToken(userId, email, role, accessExpiration);
    }

    @Override
    public String generateAccessToken(UUID userId, String email, String role, long expirationMinutes) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMinutes * 60_000))
                .signWith(key)
                .compact();
    }


    @Override
    public String refreshToken(UUID userId) {
        String refreshToken = randomToken();

        dsl.insertInto(REFRESH_TOKENS)
                .set(REFRESH_TOKENS.USER_ID, userId)
                .set(REFRESH_TOKENS.TOKEN_HASH, sha256(refreshToken))
                .set(REFRESH_TOKENS.EXPIRES_AT, OffsetDateTime.now().plusHours(refreshTokenExpiration))
                .execute();

        return refreshToken;
    }

    @Override
    @Transactional
    public AuthResponse rotateRefreshToken(String oldToken) {
        String hash = sha256(oldToken);

         RefreshTokensRecord token = dsl.selectFrom(REFRESH_TOKENS)
                .where(REFRESH_TOKENS.TOKEN_HASH.eq(hash))
                .and(REFRESH_TOKENS.REVOKED_AT.isNull())
                .and(REFRESH_TOKENS.EXPIRES_AT.gt(OffsetDateTime.now()))
                .forUpdate()
                .fetchOne();

        if (token == null) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }



        dsl.update(REFRESH_TOKENS)
                .set(REFRESH_TOKENS.REVOKED_AT, OffsetDateTime.now())
                .where(REFRESH_TOKENS.ID.eq(token.getId()))
                .execute();


        String email = dsl.select(USERS.EMAIL).from(USERS)
                .where(USERS.ID.eq(token.getUserId()))
                .fetchOne(USERS.EMAIL);

        return issueTokens(token.getUserId(), email, "BUYER");
    }

    @Override
    public Claims validateAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
