package ru.registry.provider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record Provider(String id, String baseUrl, String apiKey) {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

    public Provider {
        requireNotBlank(id, "id");
        requireNotBlank(baseUrl, "baseUrl");
        requireNotBlank(apiKey, "apiKey");
        requireTrimmed(id);
        requireHttpUrl(baseUrl);
        requireHeaderSafe(apiKey);
    }

    @Override
    public String toString() {
        return "Provider[id=" + id + ", baseUrl=" + baseUrl + ", apiKey=***]";
    }

    private static void requireNotBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireTrimmed(String id) {
        if (!id.strip().equals(id)) {
            throw new IllegalArgumentException("id must not have leading or trailing whitespace: '" + id + "'");
        }
    }

    private static void requireHttpUrl(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("baseUrl is not a valid URI: " + baseUrl, e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException("baseUrl must use http or https: " + baseUrl);
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("baseUrl must contain a host: " + baseUrl);
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("baseUrl must not contain user info");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("baseUrl must not contain query or fragment: " + baseUrl);
        }
    }

    private static void requireHeaderSafe(String apiKey) {
        if (apiKey.chars().anyMatch(c -> c < 0x21 || c > 0x7E)) {
            throw new IllegalArgumentException("apiKey must contain only visible ASCII characters");
        }
    }
}
