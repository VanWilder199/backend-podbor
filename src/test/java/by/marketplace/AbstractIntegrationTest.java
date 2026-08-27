package by.marketplace;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "telegram.bot-token=test-token")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("marketplace")
            .withUsername("marketplace")
            .withPassword("marketplace");

    private static final PropertySource<?> S3_TEST_CONFIG = loadS3TestConfig();

    private static PropertySource<?> loadS3TestConfig() {
        try {
            return new YamlPropertySourceLoader()
                    .load("s3-test-config", new ClassPathResource("application-test.yml"))
                    .get(0);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load application-test.yml for S3 test config", e);
        }
    }

    private static final String S3_BUCKET = (String) S3_TEST_CONFIG.getProperty("s3.bucket");
    private static final String S3_ACCESS_KEY = (String) S3_TEST_CONFIG.getProperty("s3.access-key");
    private static final String S3_SECRET_KEY = (String) S3_TEST_CONFIG.getProperty("s3.secret-key");

    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:latest")
            .withUserName(S3_ACCESS_KEY)
            .withPassword(S3_SECRET_KEY);

    static {
        POSTGRES.start();
        MINIO.start();

        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(MINIO.getS3URL())
                    .credentials(MINIO.getUserName(), MINIO.getPassword())
                    .build();

            client.makeBucket(MakeBucketArgs.builder().bucket(S3_BUCKET).build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create test S3 bucket", e);
        }
    }

    @DynamicPropertySource
    static void s3Properties(DynamicPropertyRegistry registry) {
        registry.add("s3.endpoint", MINIO::getS3URL);
        registry.add("s3.access-key", () -> S3_ACCESS_KEY);
        registry.add("s3.secret-key", () -> S3_SECRET_KEY);
        registry.add("s3.bucket", () -> S3_BUCKET);
        registry.add("s3.region", () -> "us-east-1");
        registry.add("s3.presigned-url-ttl-minutes", () -> "60");

    }
}
