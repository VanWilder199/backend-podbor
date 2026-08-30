package by.marketplace.car.service.impl;

import by.marketplace.car.dto.*;
import by.marketplace.car.enums.ItemStatus;
import by.marketplace.car.enums.SectionKey;
import by.marketplace.car.service.ReportService;
import by.marketplace.jooq.tables.records.*;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep4;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static by.marketplace.jooq.Tables.*;

@Service
public class ReportServiceImpl implements ReportService {
    private final DSLContext dslContext;

    public ReportServiceImpl(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    @Transactional
    @Override
    public UUID createReport(UUID inspectorId, UUID carId) {
        UUID reportId = dslContext.insertInto(REPORTS)
                .set(REPORTS.INSPECTOR_ID, inspectorId)
                .set(REPORTS.CAR_ID, carId)
                .returning(REPORTS.ID)
                .fetchOne(REPORTS.ID);

        SectionKey[] sectionKeys = SectionKey.values();

        InsertValuesStep4<ReportSectionRecord, UUID, String, Integer, String> sectionInsert = dslContext.insertInto(REPORT_SECTION,
                REPORT_SECTION.REPORT_ID, REPORT_SECTION.SECTION_KEY, REPORT_SECTION.ORDER_NO, REPORT_SECTION.SUMMARY);

        for (int i = 0; i < sectionKeys.length; i++) {
            sectionInsert.values(reportId, sectionKeys[i].name(), i + 1, "");

        }

        sectionInsert.execute();

        return reportId;
    }

    @Transactional
    @Override
    public void updateSection(UUID reportId, UUID sectionId, UUID inspectorId, UpdateSectionRequest request) {
        requireEditableReport(reportId, inspectorId);

         Optional<UUID> foundSectionId = dslContext.select(REPORT_SECTION.ID)
                 .from(REPORT_SECTION)
                .where(REPORT_SECTION.ID.eq(sectionId))
                .and(REPORT_SECTION.REPORT_ID.eq(reportId))
                 .fetchOptional(REPORT_SECTION.ID);

         if(foundSectionId.isEmpty()) {
             throw new AppException(ErrorCode.SECTION_NOT_FOUND);
         }


        dslContext.update(REPORT_SECTION)
                .set(REPORT_SECTION.SUMMARY, request.summary())
                .where(REPORT_SECTION.ID.eq(sectionId))
                .execute();

         dslContext.deleteFrom(REPORT_SECTION_ITEM)
                 .where(REPORT_SECTION_ITEM.SECTION_ID.eq(sectionId))
                 .execute();



        var insert = dslContext.insertInto(REPORT_SECTION_ITEM,
                         REPORT_SECTION_ITEM.SECTION_ID,
                         REPORT_SECTION_ITEM.ITEM_KEY,
                         REPORT_SECTION_ITEM.STATUS,
                         REPORT_SECTION_ITEM.COMMENT,
                         REPORT_SECTION_ITEM.ORDER_NO);

        int orderNo = 0;

        for (SectionItemInput item: request.items()) {
            insert.values(
                    sectionId,
                    item.itemKey(),
                    item.status().name(),
                    item.comment(),
                    orderNo++
            );
        }

        insert.execute();
    }

    @Transactional
    @Override
    public void updatePaintMeasurements(UUID reportid, UUID inspectorId, UpdatePaintMeasurementsRequest request) {
        requireEditableReport(reportid, inspectorId);

        Set<UUID> requestPanelIds = request.measurements().stream().map(PaintMeasurementInput::panelId).collect(Collectors.toSet());

        List<UUID> existingPanelIds = dslContext.select(PAINT_PANEL.ID)
                .from(PAINT_PANEL)
                .where(PAINT_PANEL.ID.in(requestPanelIds))
                .fetch(PAINT_PANEL.ID);

        if(existingPanelIds.size() != requestPanelIds.size()) {
            throw new AppException(ErrorCode.PANEL_NOT_FOUND);
        }

        dslContext.deleteFrom(PAINT_MEASUREMENT)
                .where(PAINT_MEASUREMENT.REPORT_ID.eq(reportid))
                .execute();

        var insert = dslContext.insertInto(PAINT_MEASUREMENT,
                PAINT_MEASUREMENT.REPORT_ID,
                PAINT_MEASUREMENT.PANEL_ID,
                PAINT_MEASUREMENT.SPOT,
                PAINT_MEASUREMENT.THICKNESS_UM,
                PAINT_MEASUREMENT.NOTE);

        for (PaintMeasurementInput item: request.measurements()) {
            insert.values(
                    reportid,
                    item.panelId(),
                    item.spot(),
                    item.thicknessUm(),
                    item.note()
            );
        }

        insert.execute();
    }

    @Override
    public void updateConclusion(UUID reportid, UUID inspectorId, UpdateConclusionRequest request) {
        requireEditableReport(reportid, inspectorId);

        dslContext.update(REPORTS)
                .set(REPORTS.CONCLUSION_TEXT, request.conclusionText())
                .set(REPORTS.PRICE_BYN, request.priceByn())
                .where(REPORTS.ID.eq(reportid))
                .execute();

    }

    @Override
    public void submitForModeration(UUID reportid, UUID inspectorId) {
        ReportsRecord report = requireEditableReport(reportid, inspectorId);

        var incomplete = dslContext
                .select(REPORT_SECTION.ID, DSL.count(REPORT_SECTION_ITEM.ID))
                .from(REPORT_SECTION)
                .leftJoin(REPORT_SECTION_ITEM).on(REPORT_SECTION_ITEM.SECTION_ID.eq(REPORT_SECTION.ID))
                .where(REPORT_SECTION.REPORT_ID.eq(reportid))
                .groupBy(REPORT_SECTION.ID, REPORT_SECTION.SUMMARY)
                .having(DSL.count(REPORT_SECTION_ITEM.ID).eq(0).or(REPORT_SECTION.SUMMARY.eq("")))
                .fetch();

        if (!incomplete.isEmpty()) {
            throw new AppException(ErrorCode.REPORT_INCOMPLETE);
        }

        var video = dslContext
                .selectFrom(REPORT_MEDIA)
                .where(REPORT_MEDIA.REPORT_ID.eq(reportid))
                .and(REPORT_MEDIA.SECTION_ID.isNull())
                .and(REPORT_MEDIA.KIND.eq("VIDEO"))
                .and(REPORT_MEDIA.STATUS.in("pending", "ready"))
                .fetchOptional();

        if (video.isEmpty()) {
            throw new AppException(ErrorCode.REPORT_INCOMPLETE);
        }

        if (report.getConclusionText() == null || report.getConclusionText().isBlank() || report.getPriceByn() == null) {
            throw new AppException(ErrorCode.REPORT_INCOMPLETE);
        }

        dslContext.update(REPORTS)
                .set(REPORTS.STATUS, "pending_review")
                .where(REPORTS.ID.eq(reportid))
                .execute();

    }

    @Override
    public ReportDto getReport(UUID reportId, UUID inspectorId) {
        ReportsRecord report = requireOwnedReport(reportId, inspectorId);

        List<ReportSectionRecord> sections = dslContext
                .selectFrom(REPORT_SECTION)
                .where(REPORT_SECTION.REPORT_ID.eq(reportId))
                .orderBy(REPORT_SECTION.ORDER_NO)
                .fetch();

        List<UUID> sectionIds = sections.stream().map(ReportSectionRecord::getId).toList();

        List<ReportSectionItemRecord> items = dslContext
                .selectFrom(REPORT_SECTION_ITEM)
                .where(REPORT_SECTION_ITEM.SECTION_ID.in(sectionIds))
                .fetch();

        List<ReportMediaRecord> media = dslContext.selectFrom(REPORT_MEDIA)
                .where(REPORT_MEDIA.REPORT_ID.eq(reportId))
                .fetch();


         List<MeasurementWithPanel> measurements = dslContext
                 .select(
                         PAINT_MEASUREMENT.ID,
                         PAINT_MEASUREMENT.PANEL_ID,
                         PAINT_MEASUREMENT.SPOT,
                         PAINT_MEASUREMENT.THICKNESS_UM,
                         PAINT_MEASUREMENT.NOTE,
                         PAINT_PANEL.CODE.as("panel_code"))
                 .from(PAINT_MEASUREMENT)
                 .join(PAINT_PANEL).on(PAINT_MEASUREMENT.PANEL_ID.eq(PAINT_PANEL.ID))
                 .where(PAINT_MEASUREMENT.REPORT_ID.eq(reportId))
                 .fetchInto(MeasurementWithPanel.class);

        Map<UUID, List<ReportSectionItemRecord>> itemBySectionId = items.stream()
                .collect(Collectors.groupingBy(ReportSectionItemRecord::getSectionId));

        Map<UUID, List<ReportMediaRecord>> mediaBySectionId = media.stream()
                .collect(Collectors.groupingBy(ReportMediaRecord::getSectionId, LinkedHashMap::new, Collectors.toList()));

        List<ReportMediaRecord> globalMediaRecords = media.stream()
                .filter(m -> m.getSectionId() == null)
                .toList();

        List<ReportSectionDto> sectionDtos = sections.stream()
                .map(section -> {
                    List<ReportSectionItemRecord> sectionItems =
                            itemBySectionId.getOrDefault(section.getId(), List.of());
                    List<ReportMediaRecord> sectionMedia =
                            mediaBySectionId.getOrDefault(section.getId(), List.of());

                    return toSectionDto(section, sectionItems, sectionMedia);
                })
                .toList();


        List<PaintMeasurementDto> measurementDtos = measurements.stream()
                .map(this::toMeasurementDto)
                .toList();


        List<ReportMediaDto> globalMediaDtos = globalMediaRecords.stream()
                .map(this::toMediaDto)
                .toList();

        List<String> stopFactors = items.stream()
                .filter(i -> "BAD".equals(i.getStatus()))
                .map(ReportSectionItemRecord::getItemKey)
                .toList();

        return new ReportDto(
                report.getId(),
                report.getCarId(),
                report.getInspectorId(),
                report.getVersionNo(),
                report.getStatus(),
                report.getPriceByn(),
                report.getConclusionText(),
                stopFactors,
                sectionDtos,
                measurementDtos,
                globalMediaDtos
        );

    }


    private ReportsRecord requireOwnedReport(UUID reportId, UUID inspectorId) {
        ReportsRecord report = dslContext.selectFrom(REPORTS)
                .where(REPORTS.ID.eq(reportId))
                .fetchOne();

        if (report == null) {
            throw new AppException(ErrorCode.REPORT_NOT_FOUND);
        }

        if (!report.getInspectorId().equals(inspectorId)) throw new AppException(ErrorCode.REPORT_ACCESS_DENIED);

        return report;
    };

    private ReportsRecord requireEditableReport(UUID reportId, UUID inspectorId) {
        ReportsRecord report = requireOwnedReport(reportId, inspectorId);

        if (!"draft".equals(report.getStatus())) {
            throw new AppException(ErrorCode.REPORT_NOT_EDITABLE);
        }

        return report;
    }

    private ReportSectionDto toSectionDto(
               ReportSectionRecord section,
               List<ReportSectionItemRecord> items,
               List<ReportMediaRecord> media
       ) {
             return new ReportSectionDto(
                             section.getId(),
                             section.getSectionKey(),
                             section.getOrderNo(),
                             section.getSummary(),
                             items.stream().map(this::toItemDto).toList(),
                             media.stream().map(this::toMediaDto).toList()
                     );
         }

    private SectionItemDto toItemDto(ReportSectionItemRecord item) {
            return  new SectionItemDto(
                    item.getItemKey(),
                    ItemStatus.valueOf(item.getStatus()),
                    item.getComment()
            );
    }

    private ReportMediaDto toMediaDto(ReportMediaRecord record) {
        return  new ReportMediaDto(
                record.getId(),
                record.getKind(),
                record.getS3Key(),
                record.getStatus(),
                record.getOrderNo()
        );
    }

    private PaintMeasurementDto toMeasurementDto(MeasurementWithPanel m) {
        return new PaintMeasurementDto(
                m.id(),
                m.panelCode(),
                m.spot(),
                m.thicknessUm(),
                m.note()
        );
    }

}
