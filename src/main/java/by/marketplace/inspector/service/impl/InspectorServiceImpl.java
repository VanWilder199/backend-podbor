package by.marketplace.inspector.service.impl;

import by.marketplace.auth.dto.Channel;
import by.marketplace.inspector.TelegramUser;
import by.marketplace.inspector.dto.InspectorDto;
import by.marketplace.inspector.dto.RegisterInspectorRequest;
import by.marketplace.inspector.mapper.InspectorMapper;
import by.marketplace.inspector.service.InspectorService;
import by.marketplace.jooq.tables.records.InspectorsRecord;
import by.marketplace.notification.NotificationSender;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static by.marketplace.jooq.Tables.INSPECTORS;

@Service
public class InspectorServiceImpl implements InspectorService {
    private final DSLContext dsl;
    private final InspectorMapper mapper;
    private final NotificationSender notificationSender;

    public InspectorServiceImpl(DSLContext dsl, InspectorMapper mapper, NotificationSender notificationSender) {
        this.dsl = dsl;
        this.mapper = mapper;
        this.notificationSender = notificationSender;
    }


    @Override
    public InspectorDto findByTelegramId(long telegramUserId) {
        InspectorsRecord  inspectorsRecord = dsl.selectFrom(INSPECTORS)
                .where(INSPECTORS.TELEGRAM_USER_ID.eq(telegramUserId))
                .limit(1)
                .fetchOne();

        if (inspectorsRecord == null) {
            throw new AppException(ErrorCode.INSPECTOR_NOT_FOUND);
        }

        return mapper.toDto(inspectorsRecord);
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

    @Override
    public List<InspectorDto> listInspectors(String status) {
        Condition condition = status == null ? DSL.noCondition() : INSPECTORS.STATUS.eq(status);

        return dsl.selectFrom(INSPECTORS)
                .where(condition)
                .fetch()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public InspectorDto verifyInspector(UUID inspectorId, UUID adminId) {
         InspectorsRecord inspector = requirePendingInspector(inspectorId);

        dsl.update(INSPECTORS)
                .set(INSPECTORS.STATUS, "verified")
                .where(INSPECTORS.ID.eq(inspectorId))
                .execute();

         notificationSender.notify(Channel.EMAIL, inspector.getEmail(), "Ваш аккаунт подборщика подтверждён");

         return findByTelegramId(inspector.getTelegramUserId());
    }

    @Override
    @Transactional
    public InspectorDto banInspector(UUID inspectorId, UUID adminId, String reason) {
        InspectorsRecord inspectorsRecord = requireNonBannedInspector(inspectorId);

        dsl.update(INSPECTORS)
                .set(INSPECTORS.STATUS, "banned")
                .where(INSPECTORS.ID.eq(inspectorId))
                .execute();

        notificationSender.notify(Channel.EMAIL, inspectorsRecord.getEmail(),
                "Ваш аккаунт подборщика заблокирован: " + reason);

        return findByTelegramId(inspectorsRecord.getTelegramUserId());
    }

    private InspectorsRecord requireInspector(UUID id) {
        InspectorsRecord inspectorsRecord = dsl.selectFrom(INSPECTORS)
                .where(INSPECTORS.ID.eq(id))
                .fetchOne();

        if (inspectorsRecord == null) {
            throw new AppException(ErrorCode.INSPECTOR_NOT_FOUND);
        }
        return inspectorsRecord;
    }

    private InspectorsRecord requirePendingInspector(UUID id) {
        InspectorsRecord inspector = requireInspector(id);
        if (!"pending".equals(inspector.getStatus())) {
            throw new AppException(ErrorCode.INSPECTOR_NOT_PENDING);
        }
        return inspector;
    }

    private InspectorsRecord requireNonBannedInspector(UUID id) {
        InspectorsRecord inspector = requireInspector(id);
        if ("banned".equals(inspector.getStatus())) {
            throw new AppException(ErrorCode.INSPECTOR_ALREADY_BANNED);
        }
        return inspector;
    }
}
