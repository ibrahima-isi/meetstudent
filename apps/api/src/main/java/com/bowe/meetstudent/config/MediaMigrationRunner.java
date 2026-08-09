package com.bowe.meetstudent.config;

import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.repositories.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * On startup, relocates pre-existing PRIVATE media files out of the statically-served public
 * directory into the private directory, and normalizes their storage key to the {@code private/}
 * prefix. Idempotent: rows already prefixed {@code private/} are skipped, and it is a no-op on
 * fresh databases (including the test profile).
 */
@Component
@RequiredArgsConstructor
public class MediaMigrationRunner implements ApplicationRunner {

    private static final String PRIVATE_PREFIX = "private/";

    private final MediaRepository mediaRepository;

    @Value("${file.upload-dir:uploads}")
    private String publicDir;

    @Value("${file.private-dir:storage/private}")
    private String privateDir;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var legacy = mediaRepository
                .findByVisibilityAndStorageKeyNotStartingWith(MediaVisibility.PRIVATE, PRIVATE_PREFIX);
        Path publicBase = Paths.get(publicDir).toAbsolutePath().normalize();
        Path privateBase = Paths.get(privateDir).toAbsolutePath().normalize();

        for (Media media : legacy) {
            Path source = publicBase.resolve(media.getStorageKey()).normalize();
            if (!source.startsWith(publicBase) || !Files.exists(source)) {
                continue;
            }
            String ext = extensionOf(media.getStorageKey());
            String fileName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
            Path target = privateBase.resolve(fileName);
            Files.createDirectories(privateBase);
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            media.setStorageKey(PRIVATE_PREFIX + fileName);
            mediaRepository.save(media);
        }
    }

    private String extensionOf(String key) {
        int dot = key.lastIndexOf('.');
        return dot >= 0 && dot < key.length() - 1 ? key.substring(dot + 1) : "";
    }
}
