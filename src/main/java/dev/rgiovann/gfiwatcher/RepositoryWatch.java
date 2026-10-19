package dev.rgiovann.gfiwatcher;

import java.util.List;
import java.util.regex.Pattern;

/**
 * One repository to watch and the labels that mark its beginner-friendly issues.
 *
 * <p>Labels are listed per repository because their spelling differs between projects
 * ({@code good first issue}, {@code Good first issue}, {@code Good First Issue}).
 */
public record RepositoryWatch(String repo, List<String> labels) {

    private static final Pattern OWNER_AND_NAME = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");

    public RepositoryWatch {
        if (repo == null || !OWNER_AND_NAME.matcher(repo).matches()) {
            throw new IllegalArgumentException("repo must look like owner/name, was: " + repo);
        }
        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("labels must not be empty for " + repo);
        }
        for (String label : labels) {
            // A double quote would end the quoted label in the search query.
            if (label == null || label.isBlank() || label.contains("\"")) {
                throw new IllegalArgumentException("invalid label for " + repo + ": " + label);
            }
        }
        labels = List.copyOf(labels);
    }
}
