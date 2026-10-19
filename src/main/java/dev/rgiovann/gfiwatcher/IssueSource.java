package dev.rgiovann.gfiwatcher;

import java.io.IOException;
import java.util.List;

/** Where issues come from: GitHub in production, a fixed list in tests. */
interface IssueSource {

    /** Open, unassigned issues of {@code repo} carrying {@code label}, newest first. */
    List<Issue> findOpenUnassigned(String repo, String label) throws IOException, InterruptedException;
}
