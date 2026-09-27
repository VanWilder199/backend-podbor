package by.marketplace.purchase.service.impl;

import by.marketplace.car.dto.ReportDto;
import by.marketplace.car.dto.ReportMediaDto;
import by.marketplace.car.dto.ReportSectionDto;
import by.marketplace.car.service.ReportService;
import by.marketplace.config.S3Properties;
import by.marketplace.purchase.dto.ReportMediaViewDto;
import by.marketplace.purchase.dto.ReportSectionViewDto;
import by.marketplace.purchase.dto.ReportViewDto;
import by.marketplace.purchase.service.ReportAccessService;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;
import static org.apache.commons.codec.digest.DigestUtils.sha256;

@Service
public class ReportAccessServiceImpl implements ReportAccessService {
    private final DSLContext dslContext;
    private final ReportService reportService;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public ReportAccessServiceImpl(DSLContext dslContext, ReportService reportService, S3Presigner s3Presigner, S3Properties s3Properties) {
        this.dslContext = dslContext;
        this.reportService = reportService;
        this.s3Presigner = s3Presigner;
        this.s3Properties = s3Properties;
    }
    @Override
    public ReportViewDto getReportByToken(String rawToken) {
        String tokenHash = HexFormat.of().formatHex(sha256(rawToken));

        Record row = dslContext.select(
                        REPORT_ACCESS_TOKENS.EXPIRES_AT,
                        REPORT_ACCESS_TOKENS.REVOKED_AT,
                        PURCHASES.REPORT_ID,
                        REPORTS.DELETED_AT
                ).from(REPORT_ACCESS_TOKENS)
                .join(PURCHASES).on(PURCHASES.ID.eq(REPORT_ACCESS_TOKENS.PURCHASES_ID))
                .join(REPORTS).on(REPORTS.ID.eq(PURCHASES.REPORT_ID))
                .where(REPORT_ACCESS_TOKENS.TOKEN_HASH.eq(tokenHash))
                .fetchOne();

        if (row == null
                || row.get(REPORT_ACCESS_TOKENS.REVOKED_AT) != null
                || row.get(REPORT_ACCESS_TOKENS.EXPIRES_AT).isBefore(OffsetDateTime.now())
                || row.get(REPORTS.DELETED_AT) != null) {
            throw new AppException(ErrorCode.ACCESS_TOKEN_INVALID);
        }


        UUID reportId = row.get(PURCHASES.REPORT_ID);

        ReportDto report = reportService.getReportForModeration(reportId);

        return toViewDto(report);
    }

    private ReportViewDto toViewDto(ReportDto report) {
        return new ReportViewDto(
                report.id(), report.carId(), report.priceByn(), report.conclusionText(),
                report.stopFactors(),
                report.sections().stream().map(this::toSectionViewDto).toList(),
                report.paintMeasurements(),
                report.globalMedia().stream().map(this::toMediaViewDto).toList());

    }

    private ReportSectionViewDto toSectionViewDto(ReportSectionDto section) {
        return new ReportSectionViewDto(
                section.id(), section.sectionKey(), section.orderNo(), section.summary(),
                section.items(),
                section.media().stream().map(this::toMediaViewDto).toList());
    }

    private ReportMediaViewDto toMediaViewDto(ReportMediaDto media) {
        return new ReportMediaViewDto(
                media.id(), media.kind(), generateViewUrl(media.s3Key()), media.status(), media.orderNo());
    }

    private String generateViewUrl(String s3Key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.presignedUrlTtlMinutes()))
                .getObjectRequest(b -> b.bucket(s3Properties.bucket()).key(s3Key)).build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}
