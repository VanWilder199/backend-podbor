package by.marketplace.admin.service.impl;

import by.marketplace.admin.dto.AdminReportDetailDto;
import by.marketplace.admin.dto.ModerationHistroyEntryDto;
import by.marketplace.admin.dto.ModerationQueueItemDto;
import by.marketplace.admin.service.ModerationService;
import by.marketplace.auth.dto.Channel;
import by.marketplace.car.dto.ReportDto;
import by.marketplace.car.enums.ReportStatus;
import by.marketplace.car.service.ReportService;
import by.marketplace.jooq.tables.records.ReportsRecord;
import by.marketplace.notification.NotificationSender;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static by.marketplace.jooq.Tables.*;

@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {
    private final DSLContext dslContext;
    private final ReportService reportService;
    private final NotificationSender notificationService;

    @Override
    public List<ModerationQueueItemDto> getQueue() {
        return dslContext.select(REPORTS.ID, REPORTS.CAR_ID, CARS.VIN, CARS.MAKE, CARS.MODEL, CARS.YEAR,
                        REPORTS.INSPECTOR_ID, REPORTS.VERSION_NO, REPORTS.CREATED_AT)
                .from(REPORTS)
                .join(CARS).on(REPORTS.CAR_ID.eq(CARS.ID))
                .where(REPORTS.STATUS.eq(ReportStatus.PENDING_REVIEW.getCode()))
                .and(REPORTS.DELETED_AT.isNull())
                .orderBy(REPORTS.CREATED_AT.asc())
                .fetch(r -> new ModerationQueueItemDto(
                        r.get(REPORTS.ID), r.get(REPORTS.CAR_ID), r.get(CARS.VIN), r.get(CARS.MAKE),
                        r.get(CARS.MODEL), r.get(CARS.YEAR), r.get(REPORTS.INSPECTOR_ID),
                        r.get(REPORTS.VERSION_NO), r.get(REPORTS.CREATED_AT)));
    }

    @Override
    public AdminReportDetailDto getReportDetail(UUID reportId) {
        requireExistingReport(reportId);

        ReportDto reportDto = reportService.getReportForModeration(reportId);

        List<ModerationHistroyEntryDto> history = dslContext.selectFrom(MODERATION_LOG)
                .where(MODERATION_LOG.REPORT_ID.eq(reportId))
                .orderBy(MODERATION_LOG.CREATED_AT.desc())
                .fetch(r -> new ModerationHistroyEntryDto(
                        r.get(MODERATION_LOG.ADMIN_ID),
                        r.get(MODERATION_LOG.ACTION),
                        r.get(MODERATION_LOG.REASON),
                        r.get(MODERATION_LOG.CREATED_AT)));

        return new AdminReportDetailDto(reportDto, history);
    }

    @Transactional
    @Override
    public void approve(UUID reportId, UUID adminId) {
        ReportsRecord report = requirePendingReview(reportId);

        dslContext.update(REPORTS)
                .set(REPORTS.STATUS, ReportStatus.PUBLISHED.getCode())
                .where(REPORTS.ID.eq(reportId))
                .execute();

        logDecision(reportId, adminId, "approve", null);

        String inspectorEmail = getInspectorEmail(report);
        notificationService.notify(Channel.EMAIL, inspectorEmail, "Ваш отчёт одобрен и опубликован");

        dslContext.selectFrom(REPORT_REQUESTS)
                .where(REPORT_REQUESTS.CAR_ID.eq(report.getCarId()))
                .fetch()
                .forEach(request ->
                        notificationService.notify(Channel.EMAIL, request.getEmail(),
                                "Ваш запрос на отчёт был одобрен"));
    }

    @Transactional
    @Override
    public void requestRevision(UUID reportId, UUID adminId, String reasonText) {
        ReportsRecord report = requirePendingReview(reportId);

        dslContext.update(REPORTS)
                .set(REPORTS.STATUS, ReportStatus.REVISION_REQUIRED.getCode())
                .where(REPORTS.ID.eq(reportId))
                .execute();

        logDecision(reportId, adminId, "revise", reasonText);

        String inspectorEmail = getInspectorEmail(report);
        notificationService.notify(Channel.EMAIL, inspectorEmail, "Отчёт требует правок: " + reasonText);
    }

    @Transactional
    @Override
    public void delete(UUID reportId, UUID adminId, String reasonText) {
        ReportsRecord report = requireNotDeleted(reportId);

        dslContext.update(REPORTS)
                .set(REPORTS.DELETED_AT, OffsetDateTime.now())
                .where(REPORTS.ID.eq(reportId))
                .execute();

        logDecision(reportId, adminId, "delete", reasonText);

        String inspectorEmail = getInspectorEmail(report);
        notificationService.notify(Channel.EMAIL, inspectorEmail, "Ваш отчёт удалён: " + reasonText);
    }

    private ReportsRecord requireExistingReport(UUID reportId) {
        ReportsRecord report = dslContext.selectFrom(REPORTS)
                .where(REPORTS.ID.eq(reportId))
                .fetchOne();

        if (report == null) {
            throw new AppException(ErrorCode.REPORT_NOT_FOUND);
        }

        return report;
    }

    private ReportsRecord requireNotDeleted(UUID reportId) {
        ReportsRecord report = requireExistingReport(reportId);

        if (report.getDeletedAt() != null) {
            throw new AppException(ErrorCode.REPORT_ALREADY_DELETED);
        }

        return report;
    }

    private ReportsRecord requirePendingReview(UUID reportId) {
        ReportsRecord report = requireNotDeleted(reportId);

        if (!ReportStatus.PENDING_REVIEW.getCode().equals(report.getStatus())) {
            throw new AppException(ErrorCode.REPORT_NOT_PENDING_REVIEW);
        }

        return report;
    }

    private String getInspectorEmail(ReportsRecord report) {
        return dslContext.select(INSPECTORS.EMAIL)
                .from(INSPECTORS)
                .where(INSPECTORS.ID.eq(report.getInspectorId()))
                .fetchOne(INSPECTORS.EMAIL);
    }

    private void logDecision(UUID reportId, UUID adminId, String action, String reason) {
        dslContext.insertInto(MODERATION_LOG)
                .set(MODERATION_LOG.REPORT_ID, reportId)
                .set(MODERATION_LOG.ADMIN_ID, adminId)
                .set(MODERATION_LOG.ACTION, action)
                .set(MODERATION_LOG.REASON, reason)
                .execute();
    }
}