package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.enums.MediaVisibility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class MediaStorageService {

    private static final String PUBLIC_PREFIX = "public/";
    private static final String PRIVATE_PREFIX = "private/";

    @Value("${file.upload-dir:uploads}")
    private String publicDir;

    @Value("${file.private-dir:storage/private}")
    private String privateDir;

    public String store(byte[] content, String extension, MediaVisibility visibility) throws IOException {
        String fileName = UUID.randomUUID() + "." + extension;
        boolean isPublic = visibility == MediaVisibility.PUBLIC;
        String key = (isPublic ? PUBLIC_PREFIX : PRIVATE_PREFIX) + fileName;

        Path target = resolve(key);
        Files.createDirectories(target.getParent());

        Path tmp = target.resolveSibling(fileName + ".tmp");
        Files.write(tmp, content);
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        return key;
    }

    public Resource loadAsResource(String storageKey) throws IOException {
        Path path = resolve(storageKey);
        if (!Files.exists(path)) {
            throw new IOException("Media not found");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IOException("Media not readable");
            }
            return resource;
        } catch (java.net.MalformedURLException e) {
            throw new IOException("Invalid media path", e);
        }
    }

    public boolean delete(String storageKey) throws IOException {
        return Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String storageKey) throws IOException {
        Path base;
        String relative;
        if (storageKey != null && storageKey.startsWith(PRIVATE_PREFIX)) {
            base = privateBase();
            relative = storageKey.substring(PRIVATE_PREFIX.length());
        } else if (storageKey != null && storageKey.startsWith(PUBLIC_PREFIX)) {
            base = publicBase();
            relative = storageKey.substring(PUBLIC_PREFIX.length());
        } else {
            throw new IOException("Invalid storage key");
        }
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            throw new IOException("Invalid media path");
        }
        return resolved;
    }

    private Path publicBase() {
        // Public files live under the statically-served `public/` subtree of the upload dir,
        // matching the `/uploads/public/**` resource handler.
        return Paths.get(publicDir, "public").toAbsolutePath().normalize();
    }

    private Path privateBase() {
        return Paths.get(privateDir).toAbsolutePath().normalize();
    }
}
