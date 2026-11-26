package com.bowe.meetstudent.unit.config;

import com.bowe.meetstudent.config.S3MediaStorageConfiguration;
import com.bowe.meetstudent.services.DiskMediaStorage;
import com.bowe.meetstudent.services.MediaStorage;
import com.bowe.meetstudent.services.S3MediaStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

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

    @Test
    void configuresDistinctEndpointsRegionsAndAddressingModesAtDeploymentTime() {
        // AWS S3 regional virtual-hosted + temporary session token
        configuredS3()
                .withPropertyValues(
                        "file.s3.region=eu-west-3",
                        "file.s3.endpoint=",
                        "file.s3.path-style=false",
                        "file.s3.session-token=temp-session-token")
                .run(context -> {
                    S3Client client = context.getBean(S3Client.class);
                    var cfg = client.serviceClientConfiguration();
                    assertThat(cfg.region()).isEqualTo(Region.of("eu-west-3"));
                    assertThat(cfg.endpointOverride()).isEmpty();
                    assertThat(client.utilities().getUrl(b -> b.bucket("test-bucket").key("public/logo.png")).toString())
                            .isEqualTo("https://test-bucket.s3.eu-west-3.amazonaws.com/public/logo.png");
                    AwsCredentialsIdentity creds = cfg.credentialsProvider().resolveIdentity().join();
                    assertThat(creds).isInstanceOf(AwsSessionCredentials.class);
                });

        // Cloudflare R2 EU custom endpoint + region 'auto' + path-style addressing
        configuredS3()
                .withPropertyValues(
                        "file.s3.region=auto",
                        "file.s3.endpoint=https://acct123.eu.r2.cloudflarestorage.com",
                        "file.s3.path-style=true")
                .run(context -> {
                    S3Client client = context.getBean(S3Client.class);
                    var cfg = client.serviceClientConfiguration();
                    assertThat(cfg.region()).isEqualTo(Region.of("auto"));
                    assertThat(cfg.endpointOverride()).contains(URI.create("https://acct123.eu.r2.cloudflarestorage.com"));
                    assertThat(client.utilities().getUrl(b -> b.bucket("test-bucket").key("public/logo.png")).toString())
                            .isEqualTo("https://acct123.eu.r2.cloudflarestorage.com/test-bucket/public/logo.png");
                });

        // Hetzner Object Storage custom endpoint + location signing region + virtual-hosted addressing
        configuredS3()
                .withPropertyValues(
                        "file.s3.region=fsn1",
                        "file.s3.endpoint=https://fsn1.your-objectstorage.com",
                        "file.s3.path-style=false")
                .run(context -> {
                    S3Client client = context.getBean(S3Client.class);
                    var cfg = client.serviceClientConfiguration();
                    assertThat(cfg.region()).isEqualTo(Region.of("fsn1"));
                    assertThat(cfg.endpointOverride()).contains(URI.create("https://fsn1.your-objectstorage.com"));
                    assertThat(client.utilities().getUrl(b -> b.bucket("test-bucket").key("public/logo.png")).toString())
                            .isEqualTo("https://test-bucket.fsn1.your-objectstorage.com/public/logo.png");
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

    @ParameterizedTest
    @ValueSource(strings = {
            "https://user:secret@localhost",
            "https://s3.example.com?token=secret",
            "https://s3.example.com#frag",
            "ftp://s3.example.com"
    })
    void rejectsMalformedEndpointWithoutEchoingItsContents(String endpoint) {
        configuredS3().withPropertyValues("file.s3.endpoint=" + endpoint)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("MEDIA_S3_ENDPOINT must be an HTTP(S) URL without credentials, query, or fragment"));
    }

    private ApplicationContextRunner configuredS3() {
        return runner.withPropertyValues("file.storage-backend=s3", "file.s3.bucket=test-bucket",
                "file.s3.region=us-east-1", "file.s3.access-key-id=test-access", "file.s3.secret-access-key=test-secret");
    }
}
