package com.bowe.meetstudent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry){
        // Only the public subtree is ever served statically. Private files live in file.private-dir,
        // which is never registered here and is reachable only through GET /api/v1/media/{id}.
        String publicPath = Paths.get(uploadDir, "public").toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/public/**").addResourceLocations(publicPath);
    }
}
