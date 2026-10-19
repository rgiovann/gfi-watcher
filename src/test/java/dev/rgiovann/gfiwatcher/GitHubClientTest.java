package dev.rgiovann.gfiwatcher;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the client against a local HTTP server that plays GitHub. */
class GitHubClientTest {

    private static final String SEARCH_RESPONSE = """
            {
              "total_count": 2,
              "incomplete_results": false,
              "items": [
                {
                  "number": 413,
                  "title": "Demo cross-table transaction aggregation [DBZ-3014]",
                  "html_url": "https://github.com/debezium/dbz/issues/413",
                  "created_at": "2025-12-11T10:15:30Z",
                  "comments": 1
                },
                {
                  "number": 99,
                  "title": "A pull request that slipped through",
                  "html_url": "https://github.com/debezium/dbz/pull/99",
                  "created_at": "2025-12-10T08:00:00Z",
                  "pull_request": { "url": "https://api.github.com/repos/debezium/dbz/pulls/99" }
                }
              ]
            }
            """;

    private HttpServer server;
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> userAgent = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = SEARCH_RESPONSE;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search/issues", exchange -> {
            query.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            if (status == 403) {
                exchange.getResponseHeaders().add("x-ratelimit-remaining", "0");
                exchange.getResponseHeaders().add("x-ratelimit-reset", "1792000000");
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void buildsOneQueryPerLabel() {
        assertEquals("repo:debezium/dbz is:issue is:open no:assignee label:\"good first issue\"",
                GitHubClient.query("debezium/dbz", "good first issue", null));
    }

    @Test
    void limitsTheCommentsWhenConfigured() throws Exception {
        assertEquals("repo:debezium/dbz is:issue is:open no:assignee label:\"good first issue\" comments:<=0",
                GitHubClient.query("debezium/dbz", "good first issue", 0));

        new GitHubClient(serverUri(), null, 2).findOpenUnassigned("debezium/dbz", "good first issue");
        assertTrue(query.get().contains(" comments:<=2&sort=created"), query.get());
    }

    @Test
    void searchesAndParsesIssuesSkippingPullRequests() throws Exception {
        List<Issue> issues = client(null).findOpenUnassigned("debezium/dbz", "good first issue");

        assertEquals(List.of(new Issue("debezium/dbz", "good first issue", 413,
                        "Demo cross-table transaction aggregation [DBZ-3014]",
                        URI.create("https://github.com/debezium/dbz/issues/413"),
                        Instant.parse("2025-12-11T10:15:30Z"), 1)),
                issues);
        assertEquals("q=repo:debezium/dbz is:issue is:open no:assignee label:\"good first issue\""
                + "&sort=created&order=desc&per_page=100", query.get());
        assertEquals("gfi-watcher", userAgent.get());
    }

    @Test
    void sendsTheTokenOnlyWhenThereIsOne() throws Exception {
        client("  ").findOpenUnassigned("debezium/dbz", "good first issue");
        assertNull(authorization.get());

        client("abc123").findOpenUnassigned("debezium/dbz", "good first issue");
        assertEquals("Bearer abc123", authorization.get());
    }

    @Test
    void reportsAnExhaustedRateLimit() {
        status = 403;
        body = "{\"message\": \"API rate limit exceeded\"}";

        IOException e = assertThrows(IOException.class,
                () -> client(null).findOpenUnassigned("debezium/dbz", "good first issue"));
        assertTrue(e.getMessage().contains("HTTP 403"), e.getMessage());
        assertTrue(e.getMessage().contains("rate limit exhausted, resets at 2026-10-14T17:46:40Z"), e.getMessage());
        assertTrue(e.getMessage().contains("API rate limit exceeded"), e.getMessage());
    }

    @Test
    void returnsNoIssuesWhenThereAreNone() throws Exception {
        body = "{\"total_count\": 0, \"incomplete_results\": false, \"items\": []}";

        assertEquals(List.of(), client(null).findOpenUnassigned("questdb/questdb", "Good first issue"));
    }

    private GitHubClient client(String token) {
        return new GitHubClient(serverUri(), token, null);
    }

    private URI serverUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}
