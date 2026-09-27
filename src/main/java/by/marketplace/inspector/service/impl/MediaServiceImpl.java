package by.marketplace.inspector.service.impl;

import by.marketplace.config.S3Properties;
import by.marketplace.inspector.dto.MediaKind;
import by.marketplace.inspector.dto.PresignedUrlResponse;
import by.marketplace.inspector.service.MediaService;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static by.marketplace.jooq.Tables.REPORT_MEDIA;

@Slf4j
@Service
public class MediaServiceImpl  implements MediaService {

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "video/mp4"
    );

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final DSLContext dslContext;

    public MediaServiceImpl(S3Presigner s3Presigner, S3Client s3Client, S3Properties s3Properties, DSLContext dslContext) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.dslContext = dslContext;
    }

    @Override
    public PresignedUrlResponse generatePresignateUrl(
            UUID reportId,
            UUID sectionId,
            String fileName,
            String contentType
    ) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
             throw new AppException(ErrorCode.NOT_ALLOWDED_MEDIA_TYPE);
        }

        String s3Key = buildS3key(reportId, sectionId, fileName);

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.presignedUrlTtlMinutes()))
                .putObjectRequest(builder -> builder
                        .bucket(s3Properties.bucket())
                        .key(s3Key)
                        .contentType(contentType)
                ).build();

        PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(presignRequest);
        String uploadUrl = presignedPutObjectRequest.url().toString();

        log.debug("Presigned PUT issued: reportId={}, sectionId={}, s3Key={}, contentType={}, ttlMinutes={}",
                reportId, sectionId, s3Key, contentType, s3Properties.presignedUrlTtlMinutes());

        return new PresignedUrlResponse(uploadUrl, s3Key);
    }

    @Override
    public void confirmUpload(UUID reportId, UUID sectionId, String s3Key, MediaKind kind) {

        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(s3Key)
                    .build();

            long start = System.nanoTime();
            s3Client.headObject(headObjectRequest);
            log.debug("S3 headObject: s3Key={}, durationMs={}", s3Key, (System.nanoTime() - start) / 1_000_000);

            String status = kind == MediaKind.PHOTO ? "uploaded" : "pending";

            dslContext.insertInto(REPORT_MEDIA)
                    .set(REPORT_MEDIA.REPORT_ID, reportId)
                    .set(REPORT_MEDIA.SECTION_ID,sectionId)
                    .set(REPORT_MEDIA.S3_KEY, s3Key)
                    .set(REPORT_MEDIA.KIND, kind.toString())
                    .set(REPORT_MEDIA.STATUS, status)
                    .execute();

            log.info("Media confirmed: reportId={}, sectionId={}, kind={}, status={}, s3Key={}",
                    reportId, sectionId, kind, status, s3Key);

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                log.warn("Media confirm for missing object: reportId={}, s3Key={}", reportId, s3Key);
                throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
            }

            log.error("Media confirm failed: reportId={}, s3Key={}, statusCode={}, awsErrorCode={}, awsRequestId={}",
                    reportId, s3Key, e.statusCode(),
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null,
                    e.requestId(), e);

            throw new AppException(ErrorCode.UNEXPECTED_ERROR, "Media confirm failed", e);
        } catch (SdkClientException e) {
            log.error("Media confirm failed, S3 unavailable: reportId={}, s3Key={}", reportId, s3Key, e);
            throw new AppException(ErrorCode.UNEXPECTED_ERROR, "Media confirm failed", e);
        }
    }


    private String buildS3key(UUID reportId, UUID sectionId, String fileName) {
        if (sectionId != null) {
            return  String.format(
                    "reports/%s/sections/%s/%s",
                    reportId, sectionId, fileName
            );
        } else  {
            return String.format(
                    "reports/%s/%s",
                    reportId, fileName
            );
        }
    }
}
