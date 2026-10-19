package dev.rgiovann.gfiwatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatcherTest {

    @TempDir
    Path dir;

    private final FakeSource source = new FakeSource();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final Config config = new Config(30, List.of(
            new RepositoryWatch("debezium/dbz", List.of("good first issue")),
            new RepositoryWatch("questdb/questdb", List.of("Good first issue"))), null);

    @Test
    void firstCheckRecordsOpenIssuesWithoutNotifying() throws Exception {
        source.set("debezium/dbz", issue("debezium/dbz", 413), issue("debezium/dbz", 490));
        source.set("questdb/questdb", issue("questdb/questdb", 6583));

        Watcher.CheckResult result = watcher().checkOnce();

        assertEquals(new Watcher.CheckResult(0, 3, 0), result);
        assertTrue(notifier.notified.isEmpty());
    }

    @Test
    void laterChecksNotifyOnlyNewIssuesInCreationOrder() throws Exception {
        source.set("debezium/dbz", issue("debezium/dbz", 413));
        source.set("questdb/questdb");
        watcher().checkOnce();

        // Newest first, as the search API returns them.
        source.set("debezium/dbz", issue("debezium/dbz", 2901), issue("debezium/dbz", 2900), issue("debezium/dbz", 413));
        Watcher.CheckResult result = watcher().checkOnce();

        assertEquals(new Watcher.CheckResult(2, 0, 0), result);
        assertEquals(List.of(List.of(2900, 2901)), notifier.numbers());
    }

    @Test
    void seenIssuesSurviveARestart() throws Exception {
        source.set("debezium/dbz", issue("debezium/dbz", 413));
        source.set("questdb/questdb");
        watcher().checkOnce();
        source.set("debezium/dbz", issue("debezium/dbz", 2900), issue("debezium/dbz", 413));
        watcher().checkOnce();

        // A new Watcher reloads seen.json from disk, like a restart of the program.
        Watcher.CheckResult afterRestart = watcher().checkOnce();

        assertEquals(new Watcher.CheckResult(0, 0, 0), afterRestart);
        assertEquals(List.of(List.of(2900)), notifier.numbers());
    }

    @Test
    void aFailingRepositoryDoesNotStopTheOthers() throws Exception {
        source.set("questdb/questdb", issue("questdb/questdb", 6583));
        source.fail("debezium/dbz");

        Watcher.CheckResult result = watcher().checkOnce();

        assertEquals(new Watcher.CheckResult(0, 1, 1), result);
    }

    @Test
    void aRepositoryThatFailedIsBaselinedOnceItWorks() throws Exception {
        source.fail("debezium/dbz");
        source.set("questdb/questdb");
        watcher().checkOnce();

        source.set("debezium/dbz", issue("debezium/dbz", 413));
        Watcher.CheckResult result = watcher().checkOnce();

        assertEquals(new Watcher.CheckResult(0, 1, 0), result);
        assertTrue(notifier.notified.isEmpty());
    }

    @Test
    void savesTheStateSorted() throws Exception {
        source.set("debezium/dbz", issue("debezium/dbz", 490), issue("debezium/dbz", 413));
        source.set("questdb/questdb");
        watcher().checkOnce();

        String saved = Files.readString(dir.resolve("seen.json"));
        assertTrue(saved.indexOf("/issues/413") < saved.indexOf("/issues/490"), saved);
        assertTrue(saved.contains("debezium/dbz | good first issue"), saved);
    }

    private Watcher watcher() throws IOException {
        return new Watcher(config, source, SeenStore.load(dir.resolve("seen.json")), notifier, Duration.ZERO);
    }

    private static Issue issue(String repo, int number) {
        return new Issue(repo, "label", number, "Issue " + number,
                URI.create("https://github.com/" + repo + "/issues/" + number), Instant.parse("2026-10-01T00:00:00Z"), 0);
    }

    private static final class FakeSource implements IssueSource {
        private final Map<String, List<Issue>> issues = new HashMap<>();

        void set(String repo, Issue... found) {
            issues.put(repo, List.of(found));
        }

        void fail(String repo) {
            issues.put(repo, null);
        }

        @Override
        public List<Issue> findOpenUnassigned(String repo, String label) throws IOException {
            if (!issues.containsKey(repo) || issues.get(repo) == null) {
                throw new IOException("HTTP 500");
            }
            return issues.get(repo);
        }
    }

    private static final class RecordingNotifier implements Notifier {
        private final List<List<Issue>> notified = new ArrayList<>();

        @Override
        public void newIssues(List<Issue> issues) {
            notified.add(issues);
        }

        @Override
        public void info(String message) {
        }

        List<List<Integer>> numbers() {
            return notified.stream().map(batch -> batch.stream().map(Issue::number).toList()).toList();
        }
    }
}
