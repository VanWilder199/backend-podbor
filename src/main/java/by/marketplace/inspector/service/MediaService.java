package by.marketplace.inspector.service;

import by.marketplace.inspector.dto.MediaKind;
import by.marketplace.inspector.dto.PresignedUrlResponse;

import java.util.UUID;

public interface MediaService {
    PresignedUrlResponse generatePresignateUrl(
            UUID reportId,
            UUID sectionId,
            String fileName,
            String contentType
    );
    void confirmUpload(UUID reportId,UUID sectionId, String s3key, MediaKind kind);
}
