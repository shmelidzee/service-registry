package ru.registry.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://alpha.example.com", "http://127.0.0.1:8080/api/", "HTTPS://alpha.example.com"})
    void createsValidProvider(String baseUrl) {
        assertDoesNotThrow(() -> new Provider("alpha", baseUrl, "alpha-key"));
    }

    @Test
    void rejectsNullFields() {
        assertThrows(NullPointerException.class, () -> new Provider(null, "https://alpha.example.com", "key"));
        assertThrows(NullPointerException.class, () -> new Provider("alpha", null, "key"));
        assertThrows(NullPointerException.class, () -> new Provider("alpha", "https://alpha.example.com", null));
    }

    @Test
    void rejectsBlankFields() {
        assertThrows(IllegalArgumentException.class, () -> new Provider(" ", "https://alpha.example.com", "key"));
        assertThrows(IllegalArgumentException.class, () -> new Provider("alpha", "", "key"));
        assertThrows(IllegalArgumentException.class, () -> new Provider("alpha", "https://alpha.example.com", "\t"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "alpha.example.com",
            "ftp://alpha.example.com",
            "https://",
            "https://alpha example.com",
            "https://alpha.example.com?x=1",
            "https://alpha.example.com#top",
            "https://user:pass@alpha.example.com"
    })
    void rejectsInvalidBaseUrl(String baseUrl) {
        assertThrows(IllegalArgumentException.class, () -> new Provider("alpha", baseUrl, "key"));
    }

    @ParameterizedTest
    @ValueSource(strings = {" alpha", "alpha ", "alpha\n"})
    void rejectsIdWithSurroundingWhitespace(String id) {
        assertThrows(IllegalArgumentException.class, () -> new Provider(id, "https://alpha.example.com", "key"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"key\r\nX-Injected: 1", "ключ", "key with space", " key", "key\u0000", "kéy"})
    void rejectsApiKeyUnsafeForHeader(String apiKey) {
        assertThrows(IllegalArgumentException.class,
                () -> new Provider("alpha", "https://alpha.example.com", apiKey));
    }

    @Test
    void acceptsTypicalApiKey() {
        assertDoesNotThrow(() -> new Provider("alpha", "https://alpha.example.com", "Ab0-_.~+/=:!$"));
    }

    @Test
    void hidesApiKeyInToString() {
        String text = new Provider("alpha", "https://alpha.example.com", "secret-key").toString();

        assertTrue(text.contains("alpha"));
        assertFalse(text.contains("secret-key"));
    }
}
