package dev.rgiovann.gfiwatcher;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;

/** An open, unassigned issue found under one of the watched labels. */
public record Issue(String repo, String label, int number, String title, URI url, Instant createdAt, int comments) {

    /**
     * Fewest comments first, since an issue nobody has commented on is the most likely to be
     * free; among issues with the same count, the newest first. The number breaks the last tie,
     * so the order is always the same.
     */
    static final Comparator<Issue> FEWEST_COMMENTS_FIRST = Comparator.comparingInt(Issue::comments)
            .thenComparing(Issue::createdAt, Comparator.reverseOrder())
            .thenComparingInt(Issue::number);
}
