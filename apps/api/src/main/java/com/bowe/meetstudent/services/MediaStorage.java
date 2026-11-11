package com.bowe.meetstudent.services;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

/** Stores media under stable public/ or private/ keys. Callers close the streams they open. */
public interface MediaStorage {

    void write(String key, byte[] content, String contentType) throws IOException;

    Metadata stat(String key) throws IOException;

    InputStream open(String key) throws IOException;

    /** Opens only the requested bytes, starting at a zero-based offset. */
    InputStream openRange(String key, long offset, long length) throws IOException;

    /** Deleting a missing object succeeds. */
    void delete(String key) throws IOException;

    static boolean isValidKey(String key) {
        if (key == null || !(key.startsWith("public/") || key.startsWith("private/"))) {
            return false;
        }
        for (String segment : key.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || segment.indexOf('\\') >= 0 || segment.indexOf('\0') >= 0) {
                return false;
            }
        }
        return true;
    }

    record Metadata(long size, Instant lastModified) {}

    /** Reported by stat and reads when the requested key has no object. */
    class MissingObjectException extends IOException {
        public MissingObjectException() {
            super("Media file not found");
        }
    }
}
