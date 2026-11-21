package com.bowe.meetstudent.config;

import com.bowe.meetstudent.services.MediaStorage;
import com.bowe.meetstudent.services.S3MediaStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "file.storage-backend", havingValue = "s3")
public class S3MediaStorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client mediaS3Client(@Value("${file.s3.region:}") String region,
                           @Value("${file.s3.endpoint:}") String endpoint,
                           @Value("${file.s3.path-style:false}") boolean pathStyle,
                           @Value("${file.s3.access-key-id:}") String accessKey,
                           @Value("${file.s3.secret-access-key:}") String secretKey,
                           @Value("${file.s3.session-token:}") String sessionToken) {
        requireValue(region, "MEDIA_S3_REGION");
        requireValue(accessKey, "MEDIA_S3_ACCESS_KEY_ID");
        requireValue(secretKey, "MEDIA_S3_SECRET_ACCESS_KEY");
        var credentials = StringUtils.hasText(sessionToken)
                ? AwsSessionCredentials.create(accessKey, secretKey, sessionToken)
                : AwsBasicCredentials.create(accessKey, secretKey);
        var client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .chunkedEncodingEnabled(false)
                        .build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(config -> config.apiCallAttemptTimeout(Duration.ofSeconds(30))
                        .apiCallTimeout(Duration.ofSeconds(90)));
        if (StringUtils.hasText(endpoint)) {
            client.endpointOverride(parseEndpoint(endpoint));
        }
        return client.build();
    }

    @Bean
    MediaStorage s3MediaStorage(S3Client mediaS3Client, @Value("${file.s3.bucket:}") String bucket) {
        requireValue(bucket, "MEDIA_S3_BUCKET");
        return new S3MediaStorage(mediaS3Client, bucket);
    }

    private static void requireValue(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required when MEDIA_STORAGE_BACKEND=s3");
        }
    }

    private static URI parseEndpoint(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw invalidEndpoint();
        }
        if (uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
            throw invalidEndpoint();
        }
        return uri;
    }

    private static IllegalStateException invalidEndpoint() {
        return new IllegalStateException("MEDIA_S3_ENDPOINT must be an HTTP(S) URL without credentials, query, or fragment");
    }
}
