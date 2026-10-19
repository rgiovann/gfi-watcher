package dev.rgiovann.gfiwatcher;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptionsTest {

    @Test
    void readsTheCsvFile() {
        Main.Options options = Main.Options.parse(new String[] {"--csv=issues.csv"});

        assertEquals(Main.Mode.CSV, options.mode());
        assertEquals(Path.of("issues.csv"), options.csvPath());
    }

    @Test
    void defaultsToTheTray() {
        Main.Options options = Main.Options.parse(new String[0]);

        assertEquals(Main.Mode.TRAY, options.mode());
        assertNull(options.csvPath());
    }

    @Test
    void requiresAFileNameForCsv() {
        assertThrows(IllegalArgumentException.class, () -> Main.Options.parse(new String[] {"--csv"}));
        assertThrows(IllegalArgumentException.class, () -> Main.Options.parse(new String[] {"--csv="}));
    }

    @Test
    void acceptsOnlyOneMode() {
        assertThrows(IllegalArgumentException.class,
                () -> Main.Options.parse(new String[] {"--list", "--csv=issues.csv"}));
    }
}
