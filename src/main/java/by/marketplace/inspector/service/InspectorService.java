package by.marketplace.inspector.service;

import by.marketplace.inspector.TelegramUser;
import by.marketplace.inspector.dto.InspectorDto;
import by.marketplace.inspector.dto.RegisterInspectorRequest;

import java.util.List;
import java.util.UUID;

public interface InspectorService {
    InspectorDto findByTelegramId(long telegramUserId);
    InspectorDto register(TelegramUser telegramUser, RegisterInspectorRequest req);

    List<InspectorDto> listInspectors(String status);
    InspectorDto verifyInspector(UUID inspectorId, UUID adminId);
    InspectorDto banInspector(UUID inspectorId, UUID adminId, String reason);
}
