package dev.rgiovann.gfiwatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @TempDir
    Path dir;

    /** The template the user copies; config.json itself is the user's and not in the repository. */
    @Test
    void readsTheExampleConfig() throws IOException {
        Config config = Config.load(Path.of("config.example.json"));

        assertEquals(30, config.pollIntervalMinutes());
        assertEquals(4, config.repositories().size());
        assertEquals(new RepositoryWatch("questdb/questdb", List.of("Good first issue")), config.repositories().get(2));
        assertNull(config.maxComments());
    }

    @Test
    void readsAnOptionalCommentLimit() throws IOException {
        Path file = write("""
                { "pollIntervalMinutes": 30, "maxComments": 0, "repositories": [ { "repo": "a/b", "labels": ["x"] } ] }
                """);

        assertEquals(0, Config.load(file).maxComments());
    }

    @Test
    void rejectsANegativeCommentLimit() throws IOException {
        Path file = write("""
                { "pollIntervalMinutes": 30, "maxComments": -1, "repositories": [ { "repo": "a/b", "labels": ["x"] } ] }
                """);

        IOException e = assertThrows(IOException.class, () -> Config.load(file));
        assertTrue(e.getMessage().contains("maxComments"), e.getMessage());
    }

    @Test
    void rejectsAnUnknownKey() throws IOException {
        Path file = write("""
                { "pollIntervalMinute": 30, "repositories": [ { "repo": "a/b", "labels": ["x"] } ] }
                """);

        IOException e = assertThrows(IOException.class, () -> Config.load(file));
        assertTrue(e.getMessage().contains("pollIntervalMinute"), e.getMessage());
    }

    @Test
    void rejectsAPollIntervalBelowTheMinimum() throws IOException {
        Path file = write("""
                { "pollIntervalMinutes": 1, "repositories": [ { "repo": "a/b", "labels": ["x"] } ] }
                """);

        IOException e = assertThrows(IOException.class, () -> Config.load(file));
        assertTrue(e.getMessage().contains("at least 5"), e.getMessage());
    }

    @Test
    void rejectsARepoWithoutOwner() {
        assertThrows(IllegalArgumentException.class, () -> new RepositoryWatch("trino", List.of("good first issue")));
    }

    @Test
    void rejectsALabelWithAQuote() {
        assertThrows(IllegalArgumentException.class, () -> new RepositoryWatch("a/b", List.of("say \"hi\"")));
    }

    @Test
    void rejectsARepoWithoutLabels() {
        assertThrows(IllegalArgumentException.class, () -> new RepositoryWatch("a/b", List.of()));
    }

    private Path write(String json) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, json);
        return file;
    }
}
