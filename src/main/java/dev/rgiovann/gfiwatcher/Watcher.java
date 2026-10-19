package dev.rgiovann.gfiwatcher;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One check: query every watched repository and label, notify what wasn't seen before. */
final class Watcher {

    private static final Logger LOG = Logger.getLogger(Watcher.class.getName());

    private final Config config;
    private final IssueSource source;
    private final SeenStore store;
    private final Notifier notifier;
    private final Duration spacing;

    /** @param spacing pause between two requests, to stay under the search API's rate limit */
    Watcher(Config config, IssueSource source, SeenStore store, Notifier notifier, Duration spacing) {
        this.config = config;
        this.source = source;
        this.store = store;
        this.notifier = notifier;
        this.spacing = spacing;
    }

    /**
     * Synchronized because the tray's "Check now" can run while a scheduled check is running.
     * A repository that fails is logged and skipped; the others are still checked.
     */
    synchronized CheckResult checkOnce() throws InterruptedException {
        List<Issue> newIssues = new ArrayList<>();
        int baselined = 0;
        int failures = 0;
        boolean firstRequest = true;

        for (RepositoryWatch watch : config.repositories()) {
            for (String label : watch.labels()) {
                if (!firstRequest) {
                    Thread.sleep(spacing.toMillis());
                }
                firstRequest = false;

                List<Issue> found;
                try {
                    found = source.findOpenUnassigned(watch.repo(), label);
                } catch (IOException e) {
                    failures++;
                    LOG.log(Level.WARNING, "Check of " + watch.repo() + " [" + label + "] failed: " + e.getMessage());
                    continue;
                }

                if (!store.isBaselined(watch.repo(), label)) {
                    found.forEach(issue -> store.markSeen(issue.url()));
                    store.markBaselined(watch.repo(), label);
                    baselined += found.size();
                    LOG.info("Baseline for " + watch.repo() + " [" + label + "]: " + found.size() + " open issue(s)");
                    continue;
                }

                // The search returns newest first; notify in the order the issues were created.
                for (Issue issue : found.reversed()) {
                    if (store.markSeen(issue.url())) {
                        newIssues.add(issue);
                        LOG.info("New issue: " + issue.repo() + " #" + issue.number() + " " + issue.title() + " " + issue.url());
                    }
                }
            }
        }

        try {
            store.save();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not save the seen issues", e);
        }
        if (!newIssues.isEmpty()) {
            notifier.newIssues(List.copyOf(newIssues));
        }
        return new CheckResult(newIssues.size(), baselined, failures);
    }

    record CheckResult(int newIssues, int baselinedIssues, int failures) {
    }
}
