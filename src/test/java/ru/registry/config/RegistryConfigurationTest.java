package ru.registry.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import ru.registry.client.ProviderClientFactory;
import ru.registry.service.ProviderRegistry;
import ru.registry.service.UnknownProviderException;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistryConfigurationTest {

    @Nested
    @SpringJUnitConfig(RegistryConfiguration.class)
    class WithProvidersFile {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            registry.add("registry.providers-file", RegistryConfigurationTest::providersFile);
            registry.add("registry.api-key-header", () -> "Authorization");
        }

        @Autowired
        private ProviderRegistry registry;

        @Autowired
        private ProviderClientFactory factory;

        @Test
        void loadsProvidersFromConfiguredFile() {
            assertEquals(Set.of("alpha", "beta"), registry.providerIds());
        }

        @Test
        void usesConfiguredApiKeyHeader() {
            assertEquals("Authorization", factory.apiKeyHeader());
        }
    }

    @Nested
    @SpringJUnitConfig(RegistryConfiguration.class)
    class WithoutProvidersFile {

        @Autowired
        private ProviderRegistry registry;

        @Autowired
        private ProviderClientFactory factory;

        @Test
        void usesDefaultApiKeyHeader() {
            assertEquals(ProviderClientFactory.DEFAULT_API_KEY_HEADER, factory.apiKeyHeader());
        }

        @Test
        void startsWithEmptyRegistry() {
            assertEquals(Set.of(), registry.providerIds());
            assertThrows(UnknownProviderException.class, () -> registry.isPhoneAvailable("alpha", "79991234567"));
        }
    }

    private static String providersFile() {
        try {
            return Path.of(Objects.requireNonNull(
                    RegistryConfigurationTest.class.getResource("/providers.json")).toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
