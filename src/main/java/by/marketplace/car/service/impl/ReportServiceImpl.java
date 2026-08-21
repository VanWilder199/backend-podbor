package by.marketplace.car.service.impl;

import by.marketplace.car.enums.SectionKey;
import by.marketplace.car.service.ReportService;
import by.marketplace.jooq.tables.records.ReportSectionRecord;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep4;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static by.marketplace.jooq.Tables.REPORTS;
import static by.marketplace.jooq.Tables.REPORT_SECTION;

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
}
