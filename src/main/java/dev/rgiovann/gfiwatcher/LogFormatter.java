package dev.rgiovann.gfiwatcher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * One line per record: {@code 2026-10-18 19:42:39 INFO    message}.
 *
 * <p>Uses the level's English name ({@code INFO}, {@code WARNING}) rather than
 * {@code SimpleFormatter}'s translated one, which on a Portuguese Windows is {@code INFORMAÇÕES}:
 * the rest of the program's output is in English, and the translated name broke the column
 * alignment.
 */
final class LogFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
        ZonedDateTime time = ZonedDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault());
        String line = String.format("%1$tF %1$tT %2$-7s %3$s%n", time, record.getLevel().getName(), formatMessage(record));
        if (record.getThrown() == null) {
            return line;
        }
        StringWriter stackTrace = new StringWriter();
        record.getThrown().printStackTrace(new PrintWriter(stackTrace));
        return line + stackTrace;
    }
}
