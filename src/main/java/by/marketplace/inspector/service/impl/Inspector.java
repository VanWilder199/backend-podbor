package by.marketplace.inspector.service.impl;

import by.marketplace.inspector.TelegramUser;
import by.marketplace.inspector.dto.InspectorDto;
import by.marketplace.inspector.dto.RegisterInspectorRequest;
import by.marketplace.inspector.mapper.InspectorMapper;
import by.marketplace.inspector.service.InspectorService;
import by.marketplace.jooq.tables.records.InspectorsRecord;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static by.marketplace.jooq.Tables.INSPECTORS;

@Service
public class Inspector implements InspectorService {
    private final DSLContext dsl;
    private final InspectorMapper mapper;

    public Inspector(DSLContext dsl, InspectorMapper mapper) {
        this.dsl = dsl;
        this.mapper = mapper;
    }


    @Override
    public Optional<InspectorDto> findByTelegramId(long telegramUserId) {
        InspectorsRecord  inspectorsRecord = dsl.selectFrom(INSPECTORS)
                .where(INSPECTORS.TELEGRAM_USER_ID.eq(telegramUserId))
                .limit(1)
                .fetchOne();

        if (inspectorsRecord == null) {
            throw new AppException(ErrorCode.INSPECTOR_NOT_FOUND);
        }
        return Optional.of(inspectorsRecord).map(mapper::toDto);
    }

    @Override
    @Transactional
    public InspectorDto register(TelegramUser telegramUser, RegisterInspectorRequest req) {
        Optional<InspectorsRecord> inspectorsRecord =  dsl.insertInto(INSPECTORS)
                .set(INSPECTORS.TELEGRAM_USER_ID, telegramUser.id())
                .set(INSPECTORS.FULL_NAME, req.fullName())
                .set(INSPECTORS.PHONE, req.phone())
                .set(INSPECTORS.EMAIL, req.email())
                .onConflict(INSPECTORS.TELEGRAM_USER_ID)
                .doNothing()
                 .returning()
                .fetchOptional();


         return mapper.toDto(inspectorsRecord.orElseThrow(() -> new AppException(ErrorCode.INSPECTOR_ALREADY_REGISTERED)));
    }
}
