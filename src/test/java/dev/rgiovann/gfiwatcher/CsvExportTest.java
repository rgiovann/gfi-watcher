package dev.rgiovann.gfiwatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvExportTest {

    @TempDir
    Path dir;

    @Test
    void writesAHeaderAndOneLinePerIssueForExcel() throws IOException {
        Path file = dir.resolve("issues.csv");

        CsvExport.write(file, List.of(issue(413, "Demo cross-table transaction aggregation", 1)));

        byte[] bytes = Files.readAllBytes(file);
        // UTF-8 byte order mark, so Excel reads accented letters correctly.
        assertEquals(List.of((byte) 0xEF, (byte) 0xBB, (byte) 0xBF), List.of(bytes[0], bytes[1], bytes[2]));
        assertEquals("﻿"
                        + "repo;label;number;title;comments;created;url\r\n"
                        + "debezium/dbz;good first issue;413;Demo cross-table transaction aggregation;1;2025-12-11;"
                        + "https://github.com/debezium/dbz/issues/413\r\n",
                Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void keepsAccentedLetters() throws IOException {
        Path file = dir.resolve("issues.csv");

        CsvExport.write(file, List.of(issue(1, "Configuração inválida", 0)));

        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains(";Configuração inválida;"));
    }

    @Test
    void replacesAnExistingFile() throws IOException {
        Path file = dir.resolve("issues.csv");
        Files.writeString(file, "old content that is longer than the new one ".repeat(20));

        CsvExport.write(file, List.of());

        assertEquals("﻿repo;label;number;title;comments;created;url\r\n", Files.readString(file));
    }

    @Test
    void quotesValuesWithTheSeparatorQuotesOrLineBreaks() {
        assertEquals("plain title", CsvExport.field("plain title"));
        assertEquals("\"a; b\"", CsvExport.field("a; b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.field("say \"hi\""));
        assertEquals("\"two\nlines\"", CsvExport.field("two\nlines"));
    }

    @Test
    void keepsExcelFromRunningATitleAsAFormula() {
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\"", CsvExport.field("=HYPERLINK(\"http://x\")"));
        assertEquals("'+1", CsvExport.field("+1"));
        assertEquals("'-1", CsvExport.field("-1"));
        assertEquals("'@SUM(A1)", CsvExport.field("@SUM(A1)"));
        assertEquals("[UI] a-b", CsvExport.field("[UI] a-b"));
    }

    private static Issue issue(int number, String title, int comments) {
        return new Issue("debezium/dbz", "good first issue", number, title,
                URI.create("https://github.com/debezium/dbz/issues/" + number),
                Instant.parse("2025-12-11T10:15:30Z"), comments);
    }
}
