package dev.rgiovann.gfiwatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Remembers which issues were already seen, so a restart doesn't notify them again.
 *
 * <p>A repository and label pair is "baselined" after its first successful check: the issues
 * open at that moment are recorded without a notification, and only issues created later are
 * notified.
 */
final class SeenStore {

    private final Path file;
    private final SortedSet<String> baselined = new TreeSet<>();
    private final SortedSet<String> seen = new TreeSet<>();
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private SeenStore(Path file) {
        this.file = file;
    }

    static SeenStore load(Path file) throws IOException {
        SeenStore store = new SeenStore(file);
        if (Files.exists(file)) {
            State state = store.mapper.readValue(file.toFile(), State.class);
            if (state.baselined() != null) {
                store.baselined.addAll(state.baselined());
            }
            if (state.seen() != null) {
                store.seen.addAll(state.seen());
            }
        }
        return store;
    }

    boolean isBaselined(String repo, String label) {
        return baselined.contains(key(repo, label));
    }

    void markBaselined(String repo, String label) {
        baselined.add(key(repo, label));
    }

    /** @return {@code true} if the issue had not been seen before */
    boolean markSeen(URI issueUrl) {
        return seen.add(issueUrl.toString());
    }

    /** Writes to a temporary file first, so a crash mid-write never leaves a corrupt file. */
    void save() throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temp.toFile(), new State(List.copyOf(baselined), List.copyOf(seen)));
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** A repository name has no spaces, so this key is unambiguous. */
    private static String key(String repo, String label) {
        return repo + " | " + label;
    }

    record State(List<String> baselined, List<String> seen) {
    }
}
