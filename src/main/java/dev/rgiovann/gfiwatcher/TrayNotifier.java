package dev.rgiovann.gfiwatcher;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * An icon in the system tray (next to the clock on Windows) that shows a notification for new
 * issues. Clicking the notification, or "Open latest issue" in the icon's menu, opens the
 * newest notified issue in the browser.
 */
final class TrayNotifier implements Notifier {

    private static final Logger LOG = Logger.getLogger(TrayNotifier.class.getName());
    private static final int MAX_TITLES_IN_SUMMARY = 3;

    private final TrayIcon icon;
    private volatile URI latestIssue;

    private TrayNotifier(TrayIcon icon) {
        this.icon = icon;
    }

    static boolean isSupported() {
        return SystemTray.isSupported();
    }

    static TrayNotifier install(Runnable checkNow, Runnable exit) throws AWTException {
        PopupMenu menu = new PopupMenu();
        MenuItem check = new MenuItem("Check now");
        MenuItem open = new MenuItem("Open latest issue");
        MenuItem quit = new MenuItem("Exit");
        menu.add(check);
        menu.add(open);
        menu.addSeparator();
        menu.add(quit);

        TrayIcon icon = new TrayIcon(createImage(), "gfi-watcher", menu);
        icon.setImageAutoSize(true);
        TrayNotifier notifier = new TrayNotifier(icon);

        check.addActionListener(e -> checkNow.run());
        open.addActionListener(e -> notifier.openLatestIssue());
        quit.addActionListener(e -> exit.run());
        // On Windows this fires when the notification is clicked (and on a double-click of the icon).
        icon.addActionListener(e -> notifier.openLatestIssue());

        SystemTray.getSystemTray().add(icon);
        return notifier;
    }

    @Override
    public void newIssues(List<Issue> issues) {
        Issue newest = issues.getLast();
        latestIssue = newest.url();
        String caption;
        String text;
        if (issues.size() == 1) {
            caption = "New good first issue: " + newest.repo() + " #" + newest.number();
            text = newest.title() + "\n(" + ConsoleNotifier.comments(newest.comments()) + ")";
        } else {
            caption = issues.size() + " new good first issues";
            text = issues.reversed().stream()
                    .limit(MAX_TITLES_IN_SUMMARY)
                    .map(issue -> issue.repo() + " #" + issue.number() + ": " + issue.title())
                    .collect(Collectors.joining("\n"));
        }
        EventQueue.invokeLater(() -> icon.displayMessage(caption, text, TrayIcon.MessageType.INFO));
    }

    @Override
    public void info(String message) {
        EventQueue.invokeLater(() -> icon.displayMessage("gfi-watcher", message, TrayIcon.MessageType.NONE));
    }

    void remove() {
        EventQueue.invokeLater(() -> SystemTray.getSystemTray().remove(icon));
    }

    private void openLatestIssue() {
        URI url = latestIssue;
        if (url == null) {
            info("No new issue notified yet.");
            return;
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            LOG.warning("Cannot open a browser on this system; latest issue: " + url);
            return;
        }
        try {
            Desktop.getDesktop().browse(url);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not open " + url, e);
        }
    }

    /** Draws the icon at runtime, so the jar needs no image file. */
    private static Image createImage() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x2E7D32));
        g.fillOval(1, 1, size - 2, size - 2);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        FontMetrics metrics = g.getFontMetrics();
        String letter = "G";
        g.drawString(letter, (size - metrics.stringWidth(letter)) / 2,
                (size - metrics.getHeight()) / 2 + metrics.getAscent());
        g.dispose();
        return image;
    }
}
