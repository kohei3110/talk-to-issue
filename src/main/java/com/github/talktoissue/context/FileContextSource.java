package com.github.talktoissue.context;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads context from a local file.
 */
public class FileContextSource implements ContextSource {

    private final Path path;

    public FileContextSource(Path path) {
        this.path = path;
    }

    @Override
    public String getType() {
        return "file";
    }

    @Override
    public String describe() {
        return "Local file: " + path;
    }

    @Override
    public String fetch() throws Exception {
        return Files.readString(path);
    }
}
