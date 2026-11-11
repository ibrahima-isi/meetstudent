package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.services.MediaStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.context.request.ServletWebRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Writes media without buffering the full response or discarding bytes before a requested range. */
@Component
@RequiredArgsConstructor
class MediaContentResponse {

    private final MediaStorage storage;

    static void setSecurityHeaders(HttpServletResponse response) {
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    void write(HttpServletRequest request, HttpServletResponse response,
               String key, String contentType, boolean useLastModified) throws IOException {
        MediaStorage.Metadata metadata = storage.stat(key);
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            response.setHeader(HttpHeaders.ALLOW, "GET, HEAD, OPTIONS");
            return;
        }
        if (useLastModified && new ServletWebRequest(request, response)
                .checkNotModified(metadata.lastModified().toEpochMilli())) {
            return;
        }

        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        List<Range> ranges;
        try {
            ranges = rangesFor(request.getHeader(HttpHeaders.RANGE), metadata.size());
        } catch (IllegalArgumentException e) {
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + metadata.size());
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            return;
        }

        boolean head = HttpMethod.HEAD.matches(request.getMethod());
        try {
            if (ranges.size() > 1) {
                writeMultipart(response, key, contentType, metadata.size(), ranges, head);
            } else {
                writeSingle(response, key, contentType, metadata.size(), ranges.isEmpty() ? null : ranges.getFirst(), head);
            }
        } catch (IOException e) {
            if (!response.isCommitted()) {
                response.resetBuffer();
                response.setContentType(null);
                response.setHeader(HttpHeaders.CONTENT_LENGTH, null);
                response.setHeader(HttpHeaders.CONTENT_RANGE, null);
            }
            throw e;
        }
    }

    private void writeSingle(HttpServletResponse response, String key, String contentType, long size,
                             Range range, boolean head) throws IOException {
        if (head) {
            setSingleHeaders(response, contentType, size, range);
            return;
        }
        try (var input = range == null ? storage.open(key) : storage.openRange(key, range.start(), range.length())) {
            setSingleHeaders(response, contentType, size, range);
            input.transferTo(response.getOutputStream());
        }
    }

    private void setSingleHeaders(HttpServletResponse response, String contentType, long size, Range range) {
        response.setContentType(contentType);
        if (range == null) {
            response.setContentLengthLong(size);
            return;
        }
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader(HttpHeaders.CONTENT_RANGE, range.contentRange(size));
        response.setContentLengthLong(range.length());
    }

    private void writeMultipart(HttpServletResponse response, String key, String contentType, long size,
                                List<Range> ranges, boolean head) throws IOException {
        String boundary = MimeTypeUtils.generateMultipartBoundaryString();
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setContentType("multipart/byteranges; boundary=" + boundary);
        if (head) {
            return;
        }

        OutputStream output = response.getOutputStream();
        for (Range range : ranges) {
            try (var input = storage.openRange(key, range.start(), range.length())) {
                writeAscii(output, "\r\n--" + boundary + "\r\nContent-Type: " + contentType
                        + "\r\nContent-Range: " + range.contentRange(size) + "\r\n\r\n");
                input.transferTo(output);
            }
        }
        writeAscii(output, "\r\n--" + boundary + "--");
    }

    private void writeAscii(OutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.US_ASCII));
    }

    private List<Range> rangesFor(String header, long size) {
        List<HttpRange> requested = HttpRange.parseRanges(header);
        if (header != null && requested.isEmpty()) {
            throw new IllegalArgumentException("A Range header must contain at least one byte range");
        }
        List<Range> ranges = new ArrayList<>(requested.size());
        long remaining = size;
        for (HttpRange range : requested) {
            long start = range.getRangeStart(size);
            if (start >= size) {
                throw new IllegalArgumentException("Range starts beyond the media file");
            }
            long end = range.getRangeEnd(size);
            long length = end - start + 1;
            if (requested.size() > 1 && length >= remaining) {
                throw new IllegalArgumentException("Combined ranges must be smaller than the media file");
            }
            remaining -= length;
            ranges.add(new Range(start, length));
        }
        return ranges;
    }

    private record Range(long start, long length) {
        String contentRange(long size) {
            return "bytes " + start + "-" + (start + length - 1) + "/" + size;
        }
    }
}
