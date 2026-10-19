package dev.rgiovann.gfiwatcher;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IssueTest {

    @Test
    void sortsByFewestCommentsThenNewest() {
        List<Issue> issues = List.of(
                issue(1, "2026-01-01", 5),
                issue(2, "2026-02-01", 0),
                issue(3, "2026-03-01", 2),
                issue(4, "2026-04-01", 0),
                issue(5, "2026-01-15", 2));

        List<Integer> order = issues.stream().sorted(Issue.FEWEST_COMMENTS_FIRST).map(Issue::number).toList();

        // 0 comments: #4 (April) before #2 (February); 2 comments: #3 before #5; then 5 comments.
        assertEquals(List.of(4, 2, 3, 5, 1), order);
    }

    @Test
    void breaksAFullTieByNumber() {
        List<Issue> issues = List.of(issue(9, "2026-01-01", 0), issue(7, "2026-01-01", 0));

        assertEquals(List.of(7, 9), issues.stream().sorted(Issue.FEWEST_COMMENTS_FIRST).map(Issue::number).toList());
    }

    private static Issue issue(int number, String created, int comments) {
        return new Issue("a/b", "good first issue", number, "Issue " + number,
                URI.create("https://github.com/a/b/issues/" + number), Instant.parse(created + "T00:00:00Z"), comments);
    }
}
