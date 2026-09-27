package by.marketplace.smoke;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Smoke-test: проверяет совместимость S3-конфига приложения с реальным
 * S3-совместимым провайдером (hoster.by и т.п.) через round-trip
 * presigned PUT -> presigned GET.
 *
 * <p>Запуск: {@code ./gradlew s3SmokeTest}. Перед запуском задать env:
 * S3_ENDPOINT, S3_BUCKET, S3_ACCESS_KEY, S3_SECRET_KEY; при необходимости
 * S3_REGION (default us-east-1) и S3_PATH_STYLE (default true = path-style).
 *
 * <p>Это НЕ юнит-тест и НЕ запускается в CI. MinIO-тесты доказывают механику
 * пресайнов; этот smoke-тест проверяет, что конкретные значения конфига
 * (endpoint/region/path-style/креды) принимает конкретный провайдер.
 * См. PLAN_REPORT_VIEW.md, раздел «Проверка против реального hoster.by».
 */
public final class S3SmokeTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        String endpoint = require("S3_ENDPOINT");
        String bucket = require("S3_BUCKET");
        String accessKey = require("S3_ACCESS_KEY");
        String secretKey = require("S3_SECRET_KEY");
        String region = env("S3_REGION", "us-east-1");
        boolean pathStyle = Boolean.parseBoolean(env("S3_PATH_STYLE", "true"));

        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .build();

        String key = "smoke-test/" + UUID.randomUUID() + ".txt";
        String content = "smoke-test-" + UUID.randomUUID();

        System.out.println("endpoint   = " + endpoint);
        System.out.println("bucket     = " + bucket);
        System.out.println("region     = " + region);
        System.out.println("path-style = " + pathStyle);
        System.out.println("key        = " + key);

        try (S3Presigner presigner = S3Presigner.builder()
                        .endpointOverride(URI.create(endpoint))
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(creds))
                        .serviceConfiguration(s3Config)
                        .build();
             S3Client client = S3Client.builder()
                        .endpointOverride(URI.create(endpoint))
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(creds))
                        .forcePathStyle(pathStyle)
                        .build()) {

            // 1. presigned PUT
            String putUrl = presigner.presignPutObject(PutObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(5))
                            .putObjectRequest(PutObjectRequest.builder()
                                    .bucket(bucket).key(key).contentType("text/plain").build())
                            .build())
                    .url().toString();

            HttpResponse<String> put = send(putUrl, "PUT", content);
            System.out.println("PUT  -> " + put.statusCode());
            requireOk(put, "presigned PUT");

            // 2. presigned GET
            String getUrl = presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(5))
                            .getObjectRequest(b -> b.bucket(bucket).key(key))
                            .build())
                    .url().toString();

            HttpResponse<String> get = send(getUrl, "GET", null);
            System.out.println("GET  -> " + get.statusCode());
            requireOk(get, "presigned GET");

            // 3. round-trip content match
            if (!content.equals(get.body())) {
                throw new IllegalStateException("Mismatch: sent '" + content + "' got '" + get.body() + "'");
            }

            // 4. cleanup (best-effort)
            try {
                client.deleteObject(b -> b.bucket(bucket).key(key));
                System.out.println("cleanup: deleted " + key);
            } catch (Exception e) {
                System.out.println("cleanup: warning, could not delete " + key + " -> " + e.getMessage());
            }

            System.out.println("SMOKE PASS: presigned PUT/GET round-trip OK against " + endpoint);
        }
    }

    private static HttpResponse<String> send(String url, String method, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url));
        if (body != null) {
            builder.header("Content-Type", "text/plain")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void requireOk(HttpResponse<String> r, String what) {
        int sc = r.statusCode();
        if (sc < 200 || sc >= 300) {
            throw new IllegalStateException(what + " failed: HTTP " + sc + " body=" + r.body());
        }
    }

    private static String require(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return v;
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v;
    }
}