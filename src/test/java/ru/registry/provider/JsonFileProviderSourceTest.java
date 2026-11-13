package ru.registry.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonFileProviderSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void readsProvidersFromFile() throws IOException {
        Path file = write("""
                [
                  {"id": "alpha", "baseUrl": "https://alpha.example.com", "apiKey": "alpha-key"},
                  {"id": "beta", "baseUrl": "https://beta.example.com/api", "apiKey": "beta-key"}
                ]
                """);

        assertEquals(List.of(
                new Provider("alpha", "https://alpha.example.com", "alpha-key"),
                new Provider("beta", "https://beta.example.com/api", "beta-key")
        ), new JsonFileProviderSource(file).providers());
    }

    @Test
    void readsFileWithUtf8Bom() throws IOException {
        Path file = tempDir.resolve("bom.json");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] json = "[{\"id\":\"alpha\",\"baseUrl\":\"https://alpha.example.com\",\"apiKey\":\"k\"}]"
                .getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + json.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(json, 0, content, bom.length, json.length);
        Files.write(file, content);

        assertEquals(1, new JsonFileProviderSource(file).providers().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null"})
    void returnsEmptyList(String content) throws IOException {
        assertEquals(List.of(), new JsonFileProviderSource(write(content)).providers());
    }

    @Test
    void returnsImmutableList() throws IOException {
        Path file = write("[{\"id\":\"alpha\",\"baseUrl\":\"https://alpha.example.com\",\"apiKey\":\"k\"}]");

        List<Provider> providers = new JsonFileProviderSource(file).providers();

        assertThrows(UnsupportedOperationException.class, providers::clear);
    }

    @Test
    void failsWhenFileIsMissing() {
        Path file = tempDir.resolve("missing.json");

        assertThrows(UncheckedIOException.class, () -> new JsonFileProviderSource(file).providers());
    }

    @Test
    void failsWhenPathIsDirectory() {
        assertThrows(UncheckedIOException.class, () -> new JsonFileProviderSource(tempDir).providers());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "[{\"id\": ",
            "{\"id\":\"alpha\"}",
            "[null]",
            "[{\"id\":\"alpha\",\"baseUrl\":\"https://alpha.example.com\"}]",
            "[{\"id\":\"alpha\",\"baseUrl\":\"ftp://alpha.example.com\",\"apiKey\":\"k\"}]",
            "[{\"id\":\"alpha\",\"baseUrl\":\"https://alpha.example.com\",\"apiKey\":\"k\",\"extra\":1}]",
            "[{\"id\":\"alpha\",\"baseUrl\":\"https://alpha.example.com\",\"apiKey\":\"k\"}] trailing",
            "[{\"id\":\"alpha\",\"id\":\"beta\",\"baseUrl\":\"https://alpha.example.com\",\"apiKey\":\"k\"}]"
    })
    void failsWhenContentIsInvalid(String content) throws IOException {
        Path file = write(content);

        assertThrows(IllegalArgumentException.class, () -> new JsonFileProviderSource(file).providers());
    }

    private Path write(String content) throws IOException {
        Path file = tempDir.resolve("providers.json");
        Files.writeString(file, content);
        return file;
    }
}
