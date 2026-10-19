package dev.rgiovann.gfiwatcher;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point. Four modes:
 * <ul>
 *   <li>(no option) stay in the system tray and check every {@code pollIntervalMinutes};</li>
 *   <li>{@code --once} run one check in the console and exit;</li>
 *   <li>{@code --list} print the issues open right now, without changing what was seen;</li>
 *   <li>{@code --csv=<file>} write the same issues to a CSV file, without changing what was seen.</li>
 * </ul>
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    /** Under the search API's limits: 10 requests per minute without a token, 30 with one. */
    private static final Duration SPACING_ANONYMOUS = Duration.ofMillis(6_500);
    private static final Duration SPACING_WITH_TOKEN = Duration.ofMillis(2_100);

    private static final String USAGE = """
            Usage: java -jar gfi-watcher.jar [--once | --list | --csv=<file>] [--config=<path>]

              (no option)       stay in the system tray and check periodically
              --once            run one check in the console and exit
              --list            print the issues open right now and exit
              --csv=<file>      write the issues open right now to a CSV file and exit
              --config=<path>   config file (default: config.json next to the jar,
                                or in the current directory)

            Set the GITHUB_TOKEN environment variable to raise the API rate limit (optional).
            """;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        useUtf8ConsoleInGitBash();
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.print(USAGE);
            System.exit(2);
            return;
        }
        if (options.help()) {
            System.out.print(USAGE);
            return;
        }

        Path dataDir = dataDirectory();
        Files.createDirectories(dataDir);
        setUpLogging(dataDir);

        Path configPath = options.configPath() != null ? options.configPath() : defaultConfigPath();
        Config config;
        try {
            config = Config.load(configPath);
        } catch (IOException e) {
            if (Files.notExists(configPath)) {
                LOG.severe("Config file not found: " + configPath.toAbsolutePath()
                        + ". Copy config.example.json to config.json and edit it.");
                System.exit(2);
                return;
            }
            LOG.severe("Cannot read the config file " + configPath.toAbsolutePath() + ": " + e.getMessage());
            System.exit(2);
            return;
        }

        GitHubClient client = new GitHubClient(GitHubClient.DEFAULT_API, System.getenv("GITHUB_TOKEN"), config.maxComments());
        Duration spacing = client.hasToken() ? SPACING_WITH_TOKEN : SPACING_ANONYMOUS;
        LOG.info("Config " + configPath.toAbsolutePath() + ", data in " + dataDir
                + (client.hasToken() ? ", using GITHUB_TOKEN" : ", without a token"));

        switch (options.mode()) {
            case LIST -> System.exit(list(config, client, spacing));
            case CSV -> System.exit(csv(config, client, spacing, options.csvPath()));
            case ONCE -> System.exit(once(config, client, dataDir, spacing));
            case TRAY -> tray(config, client, dataDir, spacing);
        }
    }

    private static int list(Config config, IssueSource source, Duration spacing) throws InterruptedException {
        List<Lookup> lookups = lookUpOpenIssues(config, source, spacing);
        for (Lookup lookup : lookups) {
            System.out.println();
            if (lookup.error() != null) {
                System.out.println(lookup.repo() + " [" + lookup.label() + "]: FAILED, " + lookup.error());
                continue;
            }
            System.out.println(lookup.repo() + " [" + lookup.label() + "]: " + lookup.issues().size() + " open, unassigned");
            lookup.issues().forEach(issue -> ConsoleNotifier.print(System.out, issue));
        }
        return failures(lookups) == 0 ? 0 : 1;
    }

    /** Writes what the repositories that answered returned; the others are reported, not fatal. */
    private static int csv(Config config, IssueSource source, Duration spacing, Path file) throws InterruptedException {
        List<Lookup> lookups = lookUpOpenIssues(config, source, spacing);
        // An issue carrying two watched labels is written once.
        Map<URI, Issue> issues = new LinkedHashMap<>();
        for (Lookup lookup : lookups) {
            if (lookup.error() != null) {
                LOG.warning(lookup.repo() + " [" + lookup.label() + "] left out: " + lookup.error());
            } else {
                lookup.issues().forEach(issue -> issues.putIfAbsent(issue.url(), issue));
            }
        }
        try {
            CsvExport.write(file, List.copyOf(issues.values()));
        } catch (IOException e) {
            LOG.severe("Cannot write " + file.toAbsolutePath() + " (is it open in Excel?): " + e.getMessage());
            return 2;
        }
        System.out.println("Wrote " + issues.size() + " issue(s) to " + file.toAbsolutePath());
        return failures(lookups) == 0 ? 0 : 1;
    }

    /** One search per repository and label, each result sorted fewest comments first. */
    private static List<Lookup> lookUpOpenIssues(Config config, IssueSource source, Duration spacing)
            throws InterruptedException {
        List<Lookup> lookups = new ArrayList<>();
        boolean firstRequest = true;
        for (RepositoryWatch watch : config.repositories()) {
            for (String label : watch.labels()) {
                if (!firstRequest) {
                    Thread.sleep(spacing.toMillis());
                }
                firstRequest = false;
                try {
                    List<Issue> issues = source.findOpenUnassigned(watch.repo(), label).stream()
                            .sorted(Issue.FEWEST_COMMENTS_FIRST)
                            .toList();
                    lookups.add(new Lookup(watch.repo(), label, issues, null));
                } catch (IOException e) {
                    lookups.add(new Lookup(watch.repo(), label, List.of(), e.getMessage()));
                }
            }
        }
        return lookups;
    }

    private static long failures(List<Lookup> lookups) {
        return lookups.stream().filter(lookup -> lookup.error() != null).count();
    }

    /** The result of one search: its issues, or the error that stopped it. */
    private record Lookup(String repo, String label, List<Issue> issues, String error) {
    }

    private static int once(Config config, IssueSource source, Path dataDir, Duration spacing)
            throws IOException, InterruptedException {
        ConsoleNotifier console = new ConsoleNotifier(System.out);
        Watcher watcher = new Watcher(config, source, SeenStore.load(dataDir.resolve("seen.json")), console, spacing);
        Watcher.CheckResult result = watcher.checkOnce();
        report(result, console);
        if (result.newIssues() == 0 && result.baselinedIssues() == 0) {
            console.info("No new issues.");
        }
        return result.failures() == 0 ? 0 : 1;
    }

    private static void tray(Config config, IssueSource source, Path dataDir, Duration spacing) throws Exception {
        if (!TrayNotifier.isSupported()) {
            LOG.severe("No system tray on this system; use --once or --list instead.");
            System.exit(2);
            return;
        }
        FileLock lock = lockSingleInstance(dataDir);
        if (lock == null) {
            LOG.severe("gfi-watcher is already running.");
            System.exit(2);
            return;
        }

        SeenStore store = SeenStore.load(dataDir.resolve("seen.json"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "gfi-watcher-check");
            thread.setDaemon(true);
            return thread;
        });

        // The tray's menu needs the scheduler and the check needs the tray: wire them in two steps.
        TrayNotifier[] trayHolder = new TrayNotifier[1];
        Watcher[] watcherHolder = new Watcher[1];
        Runnable check = () -> runCheck(watcherHolder[0], trayHolder[0]);
        Runnable exit = () -> {
            LOG.info("Exiting");
            scheduler.shutdownNow();
            trayHolder[0].remove();
            System.exit(0);
        };
        trayHolder[0] = TrayNotifier.install(() -> scheduler.execute(check), exit);
        watcherHolder[0] = new Watcher(config, source, store, trayHolder[0], spacing);

        LOG.info("Running in the system tray, checking every " + config.pollIntervalMinutes() + " minutes");
        scheduler.scheduleWithFixedDelay(check, 0, config.pollIntervalMinutes(), TimeUnit.MINUTES);
    }

    /** Never throws: an exception would silently cancel all later scheduled checks. */
    private static void runCheck(Watcher watcher, Notifier notifier) {
        try {
            report(watcher.checkOnce(), notifier);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Check failed", e);
        }
    }

    private static void report(Watcher.CheckResult result, Notifier notifier) {
        if (result.baselinedIssues() > 0) {
            notifier.info(result.baselinedIssues() + " issue(s) already open were recorded; "
                    + "only issues created from now on will be notified.");
        }
        if (result.failures() > 0) {
            LOG.warning(result.failures() + " repository check(s) failed; see the log for details.");
        }
    }

    /** {@code %APPDATA%\gfi-watcher} on Windows, {@code ~/.gfi-watcher} elsewhere. */
    static Path dataDirectory() {
        String appData = System.getenv("APPDATA");
        return appData != null && !appData.isBlank()
                ? Path.of(appData, "gfi-watcher")
                : Path.of(System.getProperty("user.home"), ".gfi-watcher");
    }

    /** {@code config.json} next to the jar if there is one, so a Startup shortcut needs no working directory. */
    private static Path defaultConfigPath() {
        try {
            Path location = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                Path besideJar = location.resolveSibling("config.json");
                if (Files.exists(besideJar)) {
                    return besideJar;
                }
            }
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            // fall back to the current directory
        }
        return Path.of("config.json");
    }

    /** Keeps the lock for the life of the process; {@code null} if another instance holds it. */
    @SuppressWarnings("resource")
    private static FileLock lockSingleInstance(Path dataDir) throws IOException {
        FileChannel channel = FileChannel.open(dataDir.resolve("gfi-watcher.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = channel.tryLock();
        if (lock == null) {
            channel.close();
        }
        return lock;
    }

    /**
     * Logs to {@code gfi-watcher.0.log} in the data directory and to the console. Started with
     * {@code javaw} there is no console, so the file is where tray mode's messages end up.
     */
    private static void setUpLogging(Path dataDir) throws IOException {
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        String pattern = dataDir.resolve("gfi-watcher.%g.log").toString().replace("%", "%%").replace("%%g", "%g");
        FileHandler file = new FileHandler(pattern, 1_000_000, 2, true);
        file.setEncoding(StandardCharsets.UTF_8.name());
        file.setFormatter(new LogFormatter());
        root.addHandler(file);
        // Created after useUtf8ConsoleInGitBash(), so it writes to the UTF-8 System.err.
        ConsoleHandler stderr = new ConsoleHandler();
        stderr.setFormatter(new LogFormatter());
        root.addHandler(stderr);
    }

    /**
     * Git Bash's terminal (mintty) expects UTF-8, but on Windows Java writes to it in the Windows
     * code page, so accented letters and other non-ASCII characters in issue titles come out
     * garbled. Git Bash sets {@code MSYSTEM}; the Windows console (cmd, PowerShell) doesn't, and
     * keeps Java's default.
     */
    private static void useUtf8ConsoleInGitBash() {
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        if (windows && System.getenv("MSYSTEM") != null) {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        }
    }

    enum Mode { TRAY, ONCE, LIST, CSV }

    record Options(Mode mode, Path configPath, Path csvPath, boolean help) {

        static Options parse(String[] args) {
            Mode mode = Mode.TRAY;
            Path config = null;
            Path csv = null;
            boolean help = false;
            for (String arg : args) {
                Mode requested = switch (arg) {
                    case "--once" -> Mode.ONCE;
                    case "--list" -> Mode.LIST;
                    default -> arg.startsWith("--csv") ? Mode.CSV : null;
                };
                if (requested != null) {
                    if (mode != Mode.TRAY) {
                        throw new IllegalArgumentException("Use only one of --once, --list and --csv");
                    }
                    mode = requested;
                    if (requested == Mode.CSV) {
                        if (!arg.startsWith("--csv=") || arg.length() == "--csv=".length()) {
                            throw new IllegalArgumentException("--csv needs a file name, e.g. --csv=issues.csv");
                        }
                        csv = Path.of(arg.substring("--csv=".length()));
                    }
                } else if (arg.startsWith("--config=") && arg.length() > "--config=".length()) {
                    config = Path.of(arg.substring("--config=".length()));
                } else if (arg.equals("--help") || arg.equals("-h")) {
                    help = true;
                } else {
                    throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }
            return new Options(mode, config, csv, help);
        }
    }
}
