package by.marketplace.inspector.service;

import by.marketplace.inspector.TelegramUser;
import by.marketplace.inspector.dto.InspectorDto;
import by.marketplace.inspector.dto.RegisterInspectorRequest;

import java.util.Optional;

public interface InspectorService {
    Optional<InspectorDto> findByTelegramId(long telegramUserId);
    InspectorDto register(TelegramUser telegramUser, RegisterInspectorRequest req);
}
