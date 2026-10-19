package dev.rgiovann.gfiwatcher;

import java.io.PrintStream;
import java.util.List;

/** Prints to the console; used by {@code --once} and when no system tray is available. */
final class ConsoleNotifier implements Notifier {

    private final PrintStream out;

    ConsoleNotifier(PrintStream out) {
        this.out = out;
    }

    @Override
    public void newIssues(List<Issue> issues) {
        out.println(issues.size() + " new issue(s):");
        issues.forEach(issue -> print(out, issue));
    }

    @Override
    public void info(String message) {
        out.println(message);
    }

    static void print(PrintStream out, Issue issue) {
        out.printf("%-20s #%-6d %s  %-12s %s%n", issue.repo(), issue.number(),
                issue.createdAt().toString().substring(0, 10), comments(issue.comments()), issue.title());
        out.printf("%-20s %s%n", "", issue.url());
    }

    static String comments(int count) {
        return count == 1 ? "1 comment" : count + " comments";
    }
}
