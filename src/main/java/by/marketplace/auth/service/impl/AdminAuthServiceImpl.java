package by.marketplace.auth.service.impl;

import by.marketplace.auth.dto.*;
import by.marketplace.auth.service.AdminAuthService;
import by.marketplace.auth.service.JwtService;
import by.marketplace.config.JwtProperties;
import by.marketplace.jooq.tables.records.AdminsRecord;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.jooq.DSLContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static by.marketplace.jooq.Tables.ADMINS;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {
    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final CodeVerifier codeVerifier = buildVerifier();
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();

    public AdminAuthServiceImpl(DSLContext dsl, PasswordEncoder passwordEncoder, JwtService jwtService, JwtProperties jwtProperties) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public AdminAuthResponse login(AdminLoginRequest req) {
        AdminsRecord admin =  requireAdminByCredentials(req.email(), req.password());

        if (admin.getTotpSecret() == null) {
            throw new AppException(ErrorCode.ADMIN_TOTP_NOT_CONFIGURED);
        }

        if (!codeVerifier.isValidCode(admin.getTotpSecret(), req.totpCode())) {
            throw new AppException(ErrorCode.ADMIN_TOTP_INVALID);
        }

        long expirationMinutes = jwtProperties.getAdminAccessTokenExpiration();
        String accessToken = jwtService.generateAccessToken(admin.getId(), admin.getEmail(), "ADMIN", expirationMinutes);

        return new AdminAuthResponse(accessToken, expirationMinutes * 60L);
    }

    @Override
    public AdminTotpSetupResponse setupTotp(AdminSetupTotpRequest req) {
        AdminsRecord admin =  requireAdminByCredentials(req.email(), req.password());

        if (admin.getTotpSecret() != null) {
            throw new AppException(ErrorCode.ADMIN_TOTP_ALREADY_CONFIGURED);
        }

        String secret = secretGenerator.generate();
        dsl.update(ADMINS).set(ADMINS.TOTP_SECRET,secret).where(ADMINS.ID.eq(admin.getId())).execute();

        return new AdminTotpSetupResponse(secret, buildOtpAuthUri(req.email(), secret));
    }

    @Override
    public AdminDto getCurrentAdmin(UUID adminId) {
        AdminsRecord admin = dsl.selectFrom(ADMINS)
                .where(ADMINS.ID.eq(adminId))
                .fetchOne();

        if (admin == null) {
            throw new AppException(ErrorCode.ADMIN_INVALID_CREDENTIALS);
        }
        return new AdminDto(admin.getId(), admin.getEmail());
    }

    private AdminsRecord requireAdminByCredentials(String email, String password) {
         AdminsRecord admin =  dsl.selectFrom(ADMINS)
                    .where(ADMINS.EMAIL.eq(email))
                    .fetchOne();
         if (admin == null || !passwordEncoder.matches(password, admin.getPasswordHash())) {
             throw new AppException(ErrorCode.ADMIN_INVALID_CREDENTIALS);
         }

        return admin;
    }

    private CodeVerifier buildVerifier() {
        DefaultCodeVerifier verifier =  new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        verifier.setTimePeriod(30);
        verifier.setAllowedTimePeriodDiscrepancy(1);
        return verifier;
    }

    private String buildOtpAuthUri(String email, String secret) {
        QrData data = new QrData.Builder()
                .label(email)
                .issuer("Marketplace Admin")
                .secret(secret)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        return data.getUri();
    }
}
