package com.bowe.meetstudent.unit.config;

import com.bowe.meetstudent.config.S3MediaStorageConfiguration;
import com.bowe.meetstudent.services.DiskMediaStorage;
import com.bowe.meetstudent.services.MediaStorage;
import com.bowe.meetstudent.services.S3MediaStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class S3MediaStorageConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DiskMediaStorage.class, S3MediaStorageConfiguration.class);

    @Test
    void defaultsToDiskWithoutCloudConfiguration() {
        runner.run(context -> assertThat(context).hasSingleBean(DiskMediaStorage.class));
    }

    @Test
    void selectsS3WithoutMakingANetworkCallAtStartup() {
        configuredS3().run(context -> {
            assertThat(context).hasSingleBean(MediaStorage.class);
            assertThat(context).hasSingleBean(S3MediaStorage.class);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "bucket, MEDIA_S3_BUCKET",
            "region, MEDIA_S3_REGION",
            "access-key-id, MEDIA_S3_ACCESS_KEY_ID",
            "secret-access-key, MEDIA_S3_SECRET_ACCESS_KEY"
    })
    void rejectsMissingRequiredConfiguration(String property, String environmentVariable) {
        configuredS3().withPropertyValues("file.s3." + property + "=")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(environmentVariable + " is required when MEDIA_STORAGE_BACKEND=s3"));
    }

    @Test
    void rejectsMalformedEndpointWithoutEchoingItsContents() {
        configuredS3().withPropertyValues("file.s3.endpoint=https://user:secret@localhost")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("MEDIA_S3_ENDPOINT must be an HTTP(S) URL without credentials, query, or fragment"));
    }

    private ApplicationContextRunner configuredS3() {
        return runner.withPropertyValues("file.storage-backend=s3", "file.s3.bucket=test-bucket",
                "file.s3.region=us-east-1", "file.s3.access-key-id=test-access", "file.s3.secret-access-key=test-secret");
    }
}
