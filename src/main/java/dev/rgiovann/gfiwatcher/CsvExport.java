package dev.rgiovann.gfiwatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes issues to a CSV file that Excel in Portuguese (and other locales whose list separator is
 * a semicolon) opens with a double click: semicolon-separated, UTF-8 with a byte order mark so
 * accented letters show correctly, and CRLF line endings.
 */
final class CsvExport {

    static final char SEPARATOR = ';';
    static final List<String> HEADER = List.of("repo", "label", "number", "title", "comments", "created", "url");

    /** Excel treats a cell starting with one of these as a formula. */
    private static final String FORMULA_STARTS = "=+-@\t\r";

    private CsvExport() {
    }

    /** Creates the file, or replaces it if it exists. */
    static void write(Path file, List<Issue> issues) throws IOException {
        StringBuilder csv = new StringBuilder("﻿");
        appendLine(csv, HEADER);
        for (Issue issue : issues) {
            appendLine(csv, List.of(
                    issue.repo(),
                    issue.label(),
                    String.valueOf(issue.number()),
                    issue.title(),
                    String.valueOf(issue.comments()),
                    issue.createdAt().toString().substring(0, 10),
                    issue.url().toString()));
        }
        Files.writeString(file, csv, StandardCharsets.UTF_8);
    }

    /**
     * Anyone can open an issue, so a title is untrusted text: one starting with {@code =} could run
     * as a formula when the file is opened in Excel. Such a value gets a leading apostrophe, which
     * makes Excel read it as text. A value holding the separator, a quote or a line break is quoted,
     * with its quotes doubled.
     */
    static String field(String value) {
        String text = !value.isEmpty() && FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean needsQuotes = text.indexOf(SEPARATOR) >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        return needsQuotes ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }

    private static void appendLine(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(SEPARATOR);
            }
            csv.append(field(values.get(i)));
        }
        csv.append("\r\n");
    }
}
