package dev.rgiovann.gfiwatcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFormatterTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    void usesTheEnglishLevelNameWhateverTheLocale() {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));

        String line = new LogFormatter().format(new LogRecord(Level.INFO, "Baseline for debezium/dbz"));

        assertTrue(line.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} INFO    Baseline for debezium/dbz\\R"), line);
    }

    @Test
    void appendsTheStackTraceOfAnException() {
        LogRecord record = new LogRecord(Level.WARNING, "Could not save");
        record.setThrown(new IOException("disk full"));

        String text = new LogFormatter().format(record);

        assertTrue(text.contains(" WARNING Could not save"), text);
        assertTrue(text.contains("java.io.IOException: disk full"), text);
    }
}
