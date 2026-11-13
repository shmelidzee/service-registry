package ru.registry.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import ru.registry.json.JsonReaders;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class JsonFileProviderSource implements ProviderSource {

    private static final TypeReference<List<Provider>> PROVIDER_LIST = new TypeReference<>() {
    };

    private final Path file;
    private final ObjectReader providersReader;

    public JsonFileProviderSource(Path file) {
        this(file, new ObjectMapper());
    }

    public JsonFileProviderSource(Path file, ObjectMapper objectMapper) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.providersReader = JsonReaders.strict(
                Objects.requireNonNull(objectMapper, "objectMapper must not be null").readerFor(PROVIDER_LIST));
    }

    @Override
    public List<Provider> providers() {
        List<Provider> providers = parse(read());
        if (providers == null) {
            return List.of();
        }
        if (providers.contains(null)) {
            throw new IllegalArgumentException("Providers file " + file + " contains null entry");
        }
        return List.copyOf(providers);
    }

    private byte[] read() {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read providers file " + file, e);
        }
    }

    private List<Provider> parse(byte[] content) {
        try {
            return providersReader.readValue(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid providers file " + file + ": " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse providers file " + file, e);
        }
    }
}
