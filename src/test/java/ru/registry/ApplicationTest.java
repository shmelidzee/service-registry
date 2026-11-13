package ru.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @TempDir
    Path tempDir;

    @Test
    void printsUsageWhenArgumentsAreMissing() {
        assertEquals(Application.USAGE_ERROR, run("providers.json", "alpha"));
        assertTrue(err().contains("Usage"));
        assertEquals("", out());
    }

    @Test
    void reportsMissingProvidersFile() {
        String file = tempDir.resolve("missing.json").toString();

        assertEquals(Application.FAILURE, run(file, "alpha", "79991234567"));
        assertTrue(err().contains("missing.json"), err());
    }

    @Test
    void reportsInvalidProvidersFile() throws IOException {
        Path file = tempDir.resolve("providers.json");
        Files.writeString(file, "[{\"id\":\"alpha\"}]");

        assertEquals(Application.FAILURE, run(file.toString(), "alpha", "79991234567"));
        assertTrue(err().contains("Invalid providers file"), err());
    }

    @Test
    void reportsUnknownProvider() {
        assertEquals(Application.FAILURE, run(providersFile(), "gamma", "79991234567"));
        assertTrue(err().contains("Unknown provider: gamma"), err());
    }

    @Test
    void reportsInvalidPhone() {
        assertEquals(Application.FAILURE, run(providersFile(), "alpha", "not-a-phone"));
        assertTrue(err().contains("Invalid phone number"), err());
    }

    private int run(String... args) {
        return Application.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private static String providersFile() {
        try {
            return Path.of(Objects.requireNonNull(ApplicationTest.class.getResource("/providers.json")).toURI())
                    .toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
