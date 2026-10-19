package dev.rgiovann.gfiwatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Calls GitHub's issue search ({@code GET /search/issues}).
 *
 * <p>The search API allows 10 requests per minute without a token and 30 with one;
 * {@link Watcher} spaces the requests accordingly.
 */
final class GitHubClient implements IssueSource {

    static final URI DEFAULT_API = URI.create("https://api.github.com");

    private static final int MAX_RESULTS = 100;
    private static final int MAX_ERROR_BODY = 300;

    private final HttpClient http;
    private final URI api;
    private final String token;
    private final Integer maxComments;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param token       a GitHub token, or {@code null}/blank to call the API anonymously
     * @param maxComments the most comments an issue may have, or {@code null} for no limit
     */
    GitHubClient(URI api, String token, Integer maxComments) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);
        // Honors -Dhttps.proxyHost and friends, for networks behind a proxy.
        Optional.ofNullable(ProxySelector.getDefault()).ifPresent(builder::proxy);
        this.http = builder.build();
        this.api = api;
        this.token = token == null || token.isBlank() ? null : token.strip();
        this.maxComments = maxComments;
    }

    boolean hasToken() {
        return token != null;
    }

    @Override
    public List<Issue> findOpenUnassigned(String repo, String label) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(searchUri(repo, label))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                // GitHub rejects requests without a User-Agent.
                .header("User-Agent", "gfi-watcher");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> response = http.send(request.GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("GitHub search for " + repo + " [" + label + "] failed: HTTP "
                    + response.statusCode() + rateLimitHint(response) + ": " + abbreviate(response.body()));
        }
        return parse(repo, label, response.body());
    }

    /**
     * One query per label: in GitHub search, {@code label:a label:b} means both labels at once,
     * not either of them.
     */
    static String query(String repo, String label, Integer maxComments) {
        String query = "repo:" + repo + " is:issue is:open no:assignee label:\"" + label + "\"";
        return maxComments == null ? query : query + " comments:<=" + maxComments;
    }

    URI searchUri(String repo, String label) {
        String q = URLEncoder.encode(query(repo, label, maxComments), StandardCharsets.UTF_8);
        return api.resolve("/search/issues?q=" + q + "&sort=created&order=desc&per_page=" + MAX_RESULTS);
    }

    List<Issue> parse(String repo, String label, String json) throws IOException {
        List<Issue> issues = new ArrayList<>();
        for (JsonNode item : mapper.readTree(json).path("items")) {
            if (item.has("pull_request")) {
                continue; // the query asks for issues only; this is a safety net
            }
            issues.add(new Issue(
                    repo,
                    label,
                    item.path("number").asInt(),
                    item.path("title").asText(),
                    URI.create(item.path("html_url").asText()),
                    Instant.parse(item.path("created_at").asText()),
                    item.path("comments").asInt()));
        }
        return issues;
    }

    private static String rateLimitHint(HttpResponse<?> response) {
        boolean exhausted = response.headers().firstValue("x-ratelimit-remaining").filter("0"::equals).isPresent();
        if (!exhausted) {
            return "";
        }
        return response.headers().firstValue("x-ratelimit-reset")
                .map(reset -> " (rate limit exhausted, resets at " + Instant.ofEpochSecond(Long.parseLong(reset)) + ")")
                .orElse(" (rate limit exhausted)");
    }

    private static String abbreviate(String body) {
        String oneLine = body.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= MAX_ERROR_BODY ? oneLine : oneLine.substring(0, MAX_ERROR_BODY) + "...";
    }
}
