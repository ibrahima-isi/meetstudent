package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.services.MediaStorage;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.resource.ResourceHandlerUtils;

import java.io.IOException;

@Hidden
@RestController
@RequiredArgsConstructor
class PublicMediaController {

    private final MediaContentResponse mediaContentResponse;

    @RequestMapping(path = "/uploads/public/{*path}", method = {
            RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS})
    void read(@PathVariable String path, HttpServletRequest request, HttpServletResponse response)
            throws IOException, NoResourceFoundException {
        MediaContentResponse.setSecurityHeaders(response);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        String key = "public" + path;
        if (!MediaStorage.isValidKey(key) || ResourceHandlerUtils.shouldIgnoreInputPath(path)) {
            throw new NoResourceFoundException(method, path);
        }
        String contentType = request.getServletContext().getMimeType(path);
        if (contentType == null || MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)) {
            contentType = MediaTypeFactory.getMediaType(path).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        }
        try {
            mediaContentResponse.write(request, response, key, contentType, true);
        } catch (MediaStorage.MissingObjectException e) {
            if (response.isCommitted()) {
                throw e;
            }
            throw new NoResourceFoundException(method, path);
        }
    }
}
