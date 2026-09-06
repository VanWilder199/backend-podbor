package by.marketplace.admin.service;

import by.marketplace.admin.dto.AdminReportDetailDto;
import by.marketplace.admin.dto.ModerationQueueItemDto;

import java.util.List;
import java.util.UUID;

public interface ModerationService {
    List<ModerationQueueItemDto> getQueue();
    AdminReportDetailDto getReportDetail(UUID reportId);

    void approve(UUID reportId, UUID adminId);
    void requestRevision(UUID reportId, UUID adminId, String reasonText);
    void delete(UUID reportId, UUID adminId, String reasonText);
}
