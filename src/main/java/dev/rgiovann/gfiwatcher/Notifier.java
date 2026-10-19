package dev.rgiovann.gfiwatcher;

import java.util.List;

/** How the user hears about new issues: a tray notification, or the console. */
interface Notifier {

    /** @param issues the issues found in one check, never empty */
    void newIssues(List<Issue> issues);

    void info(String message);
}
