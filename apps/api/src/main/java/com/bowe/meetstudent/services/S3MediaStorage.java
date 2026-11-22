package com.bowe.meetstudent.services;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class S3MediaStorage implements MediaStorage {

    private final S3Client client;
    private final String bucket;

    @Override
    public void write(String key, byte[] content, String contentType) throws IOException {
        validateKey(key);
        execute("upload", () -> client.putObject(request -> request.bucket(bucket).key(key).contentType(contentType),
                RequestBody.fromBytes(content)));
    }

    @Override
    public Metadata stat(String key) throws IOException {
        validateKey(key);
        var response = read("metadata", () -> client.headObject(request -> request.bucket(bucket).key(key)));
        return new Metadata(response.contentLength(), response.lastModified());
    }

    @Override
    public InputStream open(String key) throws IOException {
        validateKey(key);
        return read("download", () -> client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build()));
    }

    @Override
    public InputStream openRange(String key, long offset, long length) throws IOException {
        validateKey(key);
        if (offset < 0 || length <= 0 || offset > Long.MAX_VALUE - (length - 1)) {
            throw new IllegalArgumentException("Invalid byte range");
        }
        String range = offset + "-" + (offset + length - 1);
        var request = GetObjectRequest.builder().bucket(bucket).key(key).range("bytes=" + range).build();
        var stream = read("range download", () -> client.getObject(request));
        var response = stream.response();
        if (response.sdkHttpResponse().statusCode() != 206
                || response.contentLength() == null || response.contentLength() != length
                || response.contentRange() == null || !response.contentRange().startsWith("bytes " + range + "/")) {
            stream.abort();
            throw new UnavailableException("range download returned an invalid response");
        }
        return stream;
    }

    @Override
    public void delete(String key) throws IOException {
        validateKey(key);
        try {
            client.deleteObject(request -> request.bucket(bucket).key(key));
        } catch (NoSuchKeyException ignored) {
            // A missing key is already deleted.
        } catch (SdkException e) {
            throw unavailable("delete", e);
        }
    }

    private <T> T read(String operation, Supplier<T> request) throws IOException {
        try {
            return request.get();
        } catch (S3Exception e) {
            if (e.statusCode() == 404 && (e.awsErrorDetails() == null
                    || !"NoSuchBucket".equals(e.awsErrorDetails().errorCode()))) {
                // HEAD can return a generic 404 for either a missing key or a missing bucket.
                execute("bucket check", () -> client.headBucket(requestBuilder -> requestBuilder.bucket(bucket)));
                throw new MissingObjectException();
            }
            throw unavailable(operation, e);
        } catch (SdkException e) {
            throw unavailable(operation, e);
        }
    }

    private <T> T execute(String operation, Supplier<T> request) throws UnavailableException {
        try {
            return request.get();
        } catch (SdkException e) {
            throw unavailable(operation, e);
        }
    }

    private static UnavailableException unavailable(String operation, SdkException error) {
        String detail = error instanceof S3Exception serviceError
                ? "HTTP " + serviceError.statusCode() : "client failure";
        return new UnavailableException(operation + ": " + detail);
    }

    private static void validateKey(String key) throws IOException {
        if (!MediaStorage.isValidKey(key)) {
            throw new IOException("Invalid storage key");
        }
    }
}
