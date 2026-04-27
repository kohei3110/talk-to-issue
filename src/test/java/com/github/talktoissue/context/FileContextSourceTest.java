package com.github.talktoissue.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileContextSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void readsExistingFile() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello World");

        var source = new FileContextSource(file);
        assertEquals("Hello World", source.fetch());
    }

    @Test
    void throwsForNonExistentFile() {
        var source = new FileContextSource(tempDir.resolve("missing.txt"));
        assertThrows(Exception.class, source::fetch);
    }

    @Test
    void readsEmptyFile() throws Exception {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "");

        var source = new FileContextSource(file);
        assertEquals("", source.fetch());
    }

    @Test
    void readsUnicodeContent() throws Exception {
        Path file = tempDir.resolve("unicode.txt");
        Files.writeString(file, "日本語テスト 🚀");

        var source = new FileContextSource(file);
        assertEquals("日本語テスト 🚀", source.fetch());
    }

    @Test
    void getTypeReturnsFile() {
        var source = new FileContextSource(tempDir.resolve("any.txt"));
        assertEquals("file", source.getType());
    }

    @Test
    void describeContainsPath() {
        Path file = tempDir.resolve("data.txt");
        var source = new FileContextSource(file);
        assertTrue(source.describe().contains("data.txt"));
    }
}
