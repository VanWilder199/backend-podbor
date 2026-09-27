package by.marketplace.purchase.service.impl;

import by.marketplace.auth.dto.Channel;
import by.marketplace.car.enums.ReportStatus;
import by.marketplace.config.AppProperties;
import by.marketplace.jooq.tables.records.PurchasesRecord;
import by.marketplace.jooq.tables.records.ReportsRecord;
import by.marketplace.notification.NotificationSender;
import by.marketplace.purchase.bepaid.BePaidCheckoutResponse;
import by.marketplace.purchase.bepaid.BePaidClient;
import by.marketplace.purchase.bepaid.BePaidWebhookPayload;
import by.marketplace.purchase.dto.InitiatePurchaseResponse;
import by.marketplace.purchase.dto.PurchaseDto;
import by.marketplace.purchase.service.PayoutService;
import by.marketplace.purchase.service.PurchaseService;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import by.marketplace.shared.logging.RequestLoggingFilter;
import by.marketplace.shared.service.IdempotencyService;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

import static by.marketplace.jooq.Tables.*;
import static org.apache.commons.codec.digest.DigestUtils.sha256;

@Slf4j
@Service
public class PurchaseServiceImpl implements PurchaseService {
    private final DSLContext dslContext;
    private final BePaidClient bePaidClient;
    private final IdempotencyService idempotencyService;
    private final PayoutService payoutService;
    private final NotificationSender notificationSender;
    private final AppProperties appProperties;

    private final ObjectMapper objectMapper;

    public final String shopId;
    private final String secretKey;

    private static final SecureRandom RANDOM = new SecureRandom();


    public PurchaseServiceImpl(
            DSLContext dslContext,
            BePaidClient bePaidClient,
            IdempotencyService idempotencyService,
            PayoutService payoutService,
            NotificationSender notificationSender,
            AppProperties appProperties,
            ObjectMapper objectMapper,
            @Value("${bepaid.shop-id}") String shopId,
            @Value("${bepaid.secret-key}") String secretKey
    ) {
        this.dslContext = dslContext;
        this.bePaidClient = bePaidClient;
        this.idempotencyService = idempotencyService;
        this.payoutService = payoutService;
        this.notificationSender = notificationSender;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.shopId = shopId;
        this.secretKey = secretKey;
    }

    @Override
    public InitiatePurchaseResponse initiatePurchase(UUID buyerId, UUID reportId) {
        Record report = requirePublishedReport(reportId);

        PurchasesRecord existsPurchases = dslContext.selectFrom(PURCHASES)
                .where(PURCHASES.BUYER_ID.eq(buyerId))
                .and(PURCHASES.REPORT_ID.eq(reportId))
                .and(PURCHASES.STATUS.in("pending", "paid"))
                .fetchOne();

        if (existsPurchases != null) {
            throw new AppException(ErrorCode.PURCHASE_ALREADY_EXISTS);
        }

        UUID purchaseId = dslContext.insertInto(PURCHASES)
                .set(PURCHASES.BUYER_ID, buyerId)
                .set(PURCHASES.REPORT_ID, reportId)
                .set(PURCHASES.AMOUNT_BYN, report.get(REPORTS.PRICE_BYN))
                .set(PURCHASES.STATUS, "pending")
                .returning(PURCHASES.ID)
                .fetchOne()
                .getId();

        log.info("Purchase created: purchaseId={}, buyerId={}, reportId={}, amountByn={}",
                purchaseId, buyerId, reportId, report.get(REPORTS.PRICE_BYN));

        String description = "Отчёт: " + report.get(CARS.MAKE) + " " + report.get(CARS.MODEL)
                + " " + report.get(CARS.YEAR);

        BePaidCheckoutResponse response;
        try {
            response = bePaidClient.createCheckout(purchaseId, report.get(REPORTS.PRICE_BYN), description);
        } catch (AppException e) {
            log.warn("Checkout creation failed, purchase marked failed: purchaseId={}, reportId={}", purchaseId, reportId);
            dslContext.update(PURCHASES)
                    .set(PURCHASES.STATUS, "failed")
                    .where(PURCHASES.ID.eq(purchaseId))
                    .execute();
            throw e;
        }

        dslContext.update(PURCHASES)
                .set(PURCHASES.BEPAID_CHECKOUT_TOKEN, response.checkout().token())
                .where(PURCHASES.ID.eq(purchaseId))
                .execute();

        log.info("Checkout created: purchaseId={}", purchaseId);

        return new InitiatePurchaseResponse(purchaseId,
                "https://checkout.bepaid.by/#/" + response.checkout().token());
    }

    @Transactional
    @Override
    public void handleWebhook(String rawBody, String authorizationHeader) {
        booleanVerifyBasicAuth(authorizationHeader);

        BePaidWebhookPayload paylaod = parseBody(rawBody);

        if (paylaod == null) {
            return;
        }

        log.info("bePaid webhook received: bepaidUid={}, status={}, trackingId={}, amount={}, currency={}",
                paylaod.transaction().uid(), paylaod.transaction().status(),
                paylaod.transaction().trackingId(), paylaod.transaction().amount(),
                paylaod.transaction().currency());

        if (!idempotencyService.claim(paylaod.transaction().uid())) {
            log.info("bePaid webhook duplicate ignored: bepaidUid={}", paylaod.transaction().uid());
            return;
        }

        UUID id;

        try {
             id = UUID.fromString(paylaod.transaction().trackingId());
        } catch (IllegalArgumentException e) {
            log.warn("bePaid webhook with invalid trackingId: bepaidUid={}, trackingId={}",
                    paylaod.transaction().uid(), truncate(paylaod.transaction().trackingId()));
            return;
        }

        PurchasesRecord purchasesRecord = dslContext.selectFrom(PURCHASES)
                .where(PURCHASES.ID.eq(id))
                .fetchOne();

        if (purchasesRecord == null) {
            log.error("bePaid webhook for unknown purchase: bepaidUid={}, trackingId={}, status={}, amount={}",
                    paylaod.transaction().uid(), paylaod.transaction().trackingId(),
                    paylaod.transaction().status(), paylaod.transaction().amount());
            return;
        }

        String status = paylaod.transaction().status();

        if ("successful".equals(status)) {
            long expected = purchasesRecord.getAmountByn();
            long actual = paylaod.transaction().amount();
            if (expected != actual) {
                log.warn("bePaid webhook amount mismatch: purchaseId={}, expected={}, actual={}",
                        purchasesRecord.getId(), expected, actual);
            }
            applySuccessfulPayment(purchasesRecord);
        } else if ("failed".equals(status) || "expired".equals(status)) {
            dslContext.update(PURCHASES)
                    .set(PURCHASES.STATUS, "failed")
                    .where(PURCHASES.ID.eq(purchasesRecord.getId()))
                    .execute();
            log.info("Purchase marked failed: purchaseId={}, bepaidUid={}, bepaidStatus={}, previousStatus={}",
                    purchasesRecord.getId(), paylaod.transaction().uid(), status, purchasesRecord.getStatus());
        } else {
            log.info("Ignoring bePaid webhook with unknown status: purchaseId={}, bepaidUid={}, bepaidStatus={}",
                    purchasesRecord.getId(), paylaod.transaction().uid(), status);
        }


    }

    @Override
    public List<PurchaseDto> getPurchases(UUID buyerId) {
        return dslContext.selectFrom(PURCHASES)
                .where(PURCHASES.BUYER_ID.eq(buyerId))
                .orderBy(PURCHASES.CREATED_AT.desc())
                .fetch(record -> new PurchaseDto(
                        record.getId(),
                        record.getReportId(),
                        record.getAmountByn(),
                        record.getStatus(),
                        record.getCreatedAt(),
                        record.getPaidAt()
                ));
    }

    private Record requirePublishedReport(UUID reportId) {
        Record report = dslContext.select(REPORTS.ID, REPORTS.PRICE_BYN, CARS.MAKE, CARS.MODEL, CARS.YEAR)
                .from(REPORTS)
                .join(CARS).on(REPORTS.CAR_ID.eq(CARS.ID))
                .where(REPORTS.ID.eq(reportId))
                .and(REPORTS.STATUS.eq(ReportStatus.PUBLISHED.getCode()))
                .fetchOne();

        if (report == null) {
            throw new AppException(ErrorCode.REPORT_NOT_PUBLISHED);
        }

        return report;
    }

    private void booleanVerifyBasicAuth(String authorizationHeader) {
        String expected = Base64.getEncoder().encodeToString(
                (shopId + ":" + secretKey).getBytes(StandardCharsets.UTF_8)
        );

        String received = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Basic ")) {
            received = authorizationHeader.substring("Basic ".length()).trim();
        }

        boolean matches = received != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));

        if (!matches) {
            log.warn("bePaid webhook rejected: invalid signature, remoteAddr={}",
                    MDC.get(RequestLoggingFilter.CLIENT_IP));
            throw new AppException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
    }

    private BePaidWebhookPayload parseBody(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, BePaidWebhookPayload.class);
        } catch (JsonProcessingException e) {
            JsonLocation location = e.getLocation();
            log.warn("Unparseable bePaid webhook: bodyLength={}, error={}, line={}, column={}",
                    rawBody.length(), e.getClass().getSimpleName(),
                    location.getLineNr(), location.getColumnNr());
            return null;
        }
    }

    @Override
    public void applySuccessfulPayment(PurchasesRecord purchase) {
        ReportsRecord report = dslContext.selectFrom(REPORTS)
                .where(REPORTS.ID.eq(purchase.getReportId()))
                .fetchOne();

        if (report == null) {
            log.error("Purchase {} references missing report {}; skipping payout", purchase.getId(), purchase.getReportId());
            return;
        }

        String previousStatus = purchase.getStatus();

        dslContext.update(PURCHASES)
                .set(PURCHASES.STATUS, "paid")
                .set(PURCHASES.PAID_AT, OffsetDateTime.now())
                .where(PURCHASES.ID.eq(purchase.getId()))
                .execute();

        log.info("Payment applied: purchaseId={}, reportId={}, buyerId={}, inspectorId={}, amountByn={}, previousStatus={}",
                purchase.getId(), purchase.getReportId(), purchase.getBuyerId(),
                report.getInspectorId(), purchase.getAmountByn(), previousStatus);

        if (!"pending".equals(previousStatus)) {
            log.warn("Payment applied to purchase in unexpected status: purchaseId={}, previousStatus={}",
                    purchase.getId(), previousStatus);
        }

        payoutService.createPayout(purchase.getId(), report.getInspectorId(), purchase.getAmountByn());

        String rawToken = createAccessToken(purchase.getId());

        String email = dslContext.select(USERS.EMAIL)
                .from(USERS)
                .where(USERS.ID.eq(purchase.getBuyerId()))
                .fetchOne(USERS.EMAIL);

        if (email != null) {
            String viewUrl = appProperties.baseUrl() + "/reports/view?token=" + rawToken;

            notificationSender.notify(Channel.EMAIL, email, "Оплата прошла успешно! Ссылка на отчет: " + viewUrl);
        } else {
            log.info("Buyer has no email, access link not sent: purchaseId={}, buyerId={}",
                    purchase.getId(), purchase.getBuyerId());
        }
    }

    private String createAccessToken(UUID purchaseId) {
        String rawToken = randomToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(30);

        dslContext.insertInto(REPORT_ACCESS_TOKENS)
                .set(REPORT_ACCESS_TOKENS.PURCHASES_ID, purchaseId)
                .set(REPORT_ACCESS_TOKENS.TOKEN_HASH, HexFormat.of().formatHex(sha256(rawToken)))
                .set(REPORT_ACCESS_TOKENS.EXPIRES_AT, expiresAt)
                .execute();

        log.debug("Access token created: purchaseId={}, expiresAt={}", purchaseId, expiresAt);
        return rawToken;
    }

      private String randomToken() {
             byte[] bytes = new byte[32];
             RANDOM.nextBytes(bytes);
             return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
         }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 64 ? value : value.substring(0, 64) + "...";
    }

}
