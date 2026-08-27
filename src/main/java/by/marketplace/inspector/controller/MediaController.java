package by.marketplace.inspector.controller;

import by.marketplace.inspector.dto.ConfirmUploadRequest;
import by.marketplace.inspector.dto.PresignedUrlRequest;
import by.marketplace.inspector.dto.PresignedUrlResponse;
import by.marketplace.inspector.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/inspector/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/presigned-url/{reportId}")
    public ResponseEntity<PresignedUrlResponse> generatePresignedUrl
            (@PathVariable UUID reportId,
             @Valid @RequestBody PresignedUrlRequest request) {
            return ResponseEntity.ok(mediaService.generatePresignateUrl(
                    reportId,
                    request.sectionId(),
                    request.fileName(),
                    request.contentType()
            ));
    }

    @PostMapping("/confirm-upload/{reportId}")
    public ResponseEntity<Void> confirmUpload(@PathVariable UUID reportId,@Valid @RequestBody ConfirmUploadRequest request) {
        mediaService.confirmUpload(
                reportId,
                request.sectionId(),
                request.s3key(),
                request.kind()
        );
        return ResponseEntity.noContent().build();
    }
}
