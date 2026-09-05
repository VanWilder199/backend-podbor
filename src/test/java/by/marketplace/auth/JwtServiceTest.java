package by.marketplace.auth;

import by.marketplace.auth.dto.AuthResponse;
import by.marketplace.auth.service.impl.JwtServiceImpl;
import by.marketplace.config.JwtProperties;
import by.marketplace.jooq.tables.records.RefreshTokensRecord;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static by.marketplace.jooq.Tables.REFRESH_TOKENS;
import static by.marketplace.jooq.Tables.USERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private JwtServiceImpl buildService(MockDataProvider provider) {
        DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);

        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-must-be-at-least-32-bytes-long!!");
        props.setAccessTokenExpiration(15);

        return new JwtServiceImpl(props, dsl);
    }

    @Test
    void rotateRefreshToken_whenTokenNotFound_throwsInvalidRefreshToken() {
        // Реальный WHERE (token_hash = ? AND revoked_at IS NULL AND expires_at > now())
        // фильтрует несуществующие, отозванные и просроченные токены одинаково —
        // на уровне БД все три случая дают 0 строк.
        MockDataProvider provider = ctx -> {
            String sql = ctx.sql().toLowerCase();
            if (sql.startsWith("select") && sql.contains("refresh_tokens")) {
                Result<RefreshTokensRecord> emptyResult = DSL.using(SQLDialect.POSTGRES).newResult(REFRESH_TOKENS);
                return new MockResult[]{new MockResult(0, emptyResult)};
            }
            throw new IllegalStateException("Unexpected SQL: " + ctx.sql());
        };

        JwtServiceImpl jwtService = buildService(provider);

        AppException ex = assertThrows(AppException.class,
                () -> jwtService.rotateRefreshToken("nonexistent-token"));

        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, ex.getErrorCode());
    }

    @Test
    void rotateRefreshToken_whenTokenValid_revokesOldAndIssuesNewTokens() {
        UUID userId = UUID.randomUUID();

        MockDataProvider provider = ctx -> {
            String sql = ctx.sql().toLowerCase();

            if (sql.startsWith("select") && sql.contains("refresh_tokens")) {
                DSLContext create = DSL.using(SQLDialect.POSTGRES);
                Result<RefreshTokensRecord> result = create.newResult(REFRESH_TOKENS);
                RefreshTokensRecord record = create.newRecord(REFRESH_TOKENS);
                record.setId(1L);
                record.setUserId(userId);
                record.setTokenHash("irrelevant-hash");
                record.setExpiresAt(OffsetDateTime.now().plusHours(1));
                record.setRevokedAt(null);
                record.setCreatedAt(OffsetDateTime.now());
                result.add(record);
                return new MockResult[]{new MockResult(1, result)};
            }

            if (sql.startsWith("update")) {
                return new MockResult[]{new MockResult(1, null)};
            }

            if (sql.startsWith("select") && sql.contains("users")) {
                DSLContext create = DSL.using(SQLDialect.POSTGRES);
                Result<Record1<String>> result = create.newResult(USERS.EMAIL);
                Record1<String> emailRecord = create.newRecord(USERS.EMAIL);
                emailRecord.setValue(USERS.EMAIL, "buyer@example.com");
                result.add(emailRecord);
                return new MockResult[]{new MockResult(1, result)};
            }

            if (sql.startsWith("insert")) {
                return new MockResult[]{new MockResult(1, null)};
            }

            throw new IllegalStateException("Unexpected SQL: " + ctx.sql());
        };

        JwtServiceImpl jwtService = buildService(provider);

        AuthResponse response = jwtService.rotateRefreshToken("valid-token");

        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
        assertNotEquals("valid-token", response.refreshToken());
    }

    @Test
    void generateAccessToken_withExplicitExpiration_overridesDefaultAccessTokenExpiration() {
        // props.accessTokenExpiration = 15 (buyer default) — админский вызов должен игнорировать
        // это значение и жить ровно столько, сколько передано явным параметром (24 часа = 1440 минут).
        JwtServiceImpl jwtService = buildService(ctx -> {
            throw new IllegalStateException("Unexpected SQL: " + ctx.sql());
        });
        UUID adminId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(adminId, "admin@example.com", "ADMIN", 1440L);
        Claims claims = jwtService.validateAccessToken(token);

        long actualTtlMinutes = Duration.between(
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant()
        ).toMinutes();

        assertEquals(1440L, actualTtlMinutes);
    }

    @Test
    void generateAccessToken_withoutExplicitExpiration_stillUsesDefaultAccessTokenExpiration() {
        // Контрольный кейс: buyer-флоу (3-аргументный метод) не должен был сломаться
        // при добавлении 4-аргументной перегрузки.
        JwtServiceImpl jwtService = buildService(ctx -> {
            throw new IllegalStateException("Unexpected SQL: " + ctx.sql());
        });
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, "buyer@example.com", "BUYER");
        Claims claims = jwtService.validateAccessToken(token);

        long actualTtlMinutes = Duration.between(
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant()
        ).toMinutes();

        assertEquals(15L, actualTtlMinutes);
    }
}
