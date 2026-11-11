package com.bowe.meetstudent.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "file.storage-backend", havingValue = "disk", matchIfMissing = true)
public class DiskMediaStorage implements MediaStorage {

    private final Path publicDir;
    private final Path privateDir;

    public DiskMediaStorage(@Value("${file.upload-dir:uploads}") String uploadDir,
                            @Value("${file.private-dir:storage/private}") String privateDir) {
        this.publicDir = Path.of(uploadDir, "public").toAbsolutePath().normalize();
        this.privateDir = Path.of(privateDir).toAbsolutePath().normalize();
    }

    @Override
    public void write(String key, byte[] content, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createFile(target.resolveSibling(".media-" + UUID.randomUUID() + ".tmp"));
        try {
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public Metadata stat(String key) throws IOException {
        try {
            var attributes = Files.readAttributes(resolve(key), BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                throw new MissingObjectException();
            }
            return new Metadata(attributes.size(), attributes.lastModifiedTime().toInstant());
        } catch (NoSuchFileException e) {
            throw new MissingObjectException();
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        try {
            return Files.newInputStream(resolve(key));
        } catch (NoSuchFileException e) {
            throw new MissingObjectException();
        }
    }

    @Override
    public InputStream openRange(String key, long offset, long length) throws IOException {
        if (offset < 0 || length <= 0) {
            throw new IllegalArgumentException("A byte range needs a non-negative offset and positive length");
        }
        FileChannel channel;
        try {
            channel = FileChannel.open(resolve(key));
        } catch (NoSuchFileException e) {
            throw new MissingObjectException();
        }
        try {
            channel.position(offset);
            return new RangeInputStream(Channels.newInputStream(channel), length);
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (MissingObjectException ignored) {
            // Symlinks are outside the set of stored media objects.
        }
    }

    private Path resolve(String key) throws IOException {
        if (!MediaStorage.isValidKey(key)) {
            throw new IOException("Invalid storage key");
        }
        Path path = key.startsWith("public/") ? publicDir : privateDir;
        for (String segment : key.substring(key.indexOf('/') + 1).split("/")) {
            path = path.resolve(segment);
            if (Files.isSymbolicLink(path)) {
                throw new MissingObjectException();
            }
        }
        return path;
    }

    private static class RangeInputStream extends InputStream {
        private final InputStream input;
        private long remaining;

        private RangeInputStream(InputStream input, long length) {
            this.input = input;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int value = input.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            if (remaining == 0) {
                return -1;
            }
            int read = input.read(bytes, offset, (int) Math.min(remaining, length));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }
}
