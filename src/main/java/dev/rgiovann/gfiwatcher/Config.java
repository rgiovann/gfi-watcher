package dev.rgiovann.gfiwatcher;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The contents of {@code config.json}. Unknown properties are rejected, so a typo in a key
 * fails at startup instead of being silently ignored.
 *
 * @param maxComments if set, only issues with at most this many comments are listed and
 *                    notified; {@code null} (the key left out) applies no limit
 */
public record Config(int pollIntervalMinutes, List<RepositoryWatch> repositories, Integer maxComments) {

    /** Keeps the watcher a polite API client; new issues are claimed in days, not minutes. */
    static final int MIN_POLL_INTERVAL_MINUTES = 5;

    public Config {
        if (pollIntervalMinutes < MIN_POLL_INTERVAL_MINUTES) {
            throw new IllegalArgumentException("pollIntervalMinutes must be at least "
                    + MIN_POLL_INTERVAL_MINUTES + ", was: " + pollIntervalMinutes);
        }
        if (repositories == null || repositories.isEmpty()) {
            throw new IllegalArgumentException("repositories must not be empty");
        }
        repositories = List.copyOf(repositories);
        if (maxComments != null && maxComments < 0) {
            throw new IllegalArgumentException("maxComments must not be negative, was: " + maxComments);
        }
    }

    public static Config load(Path path) throws IOException {
        return new ObjectMapper().readValue(path.toFile(), Config.class);
    }
}
