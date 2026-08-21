package by.marketplace.inspector.mapper;

import by.marketplace.inspector.dto.InspectorDto;
import by.marketplace.jooq.tables.records.InspectorsRecord;
import org.springframework.stereotype.Component;

@Component
public class InspectorMapper {

    public InspectorDto toDto(InspectorsRecord inspector) {
        return new InspectorDto(
                inspector.getId(),
                inspector.getTelegramUserId(),
                inspector.getFullName(),
                inspector.getPhone(),
                inspector.getEmail(),
                inspector.getStatus()
        );
    }
}
