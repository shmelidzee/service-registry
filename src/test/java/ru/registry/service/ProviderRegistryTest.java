package ru.registry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.registry.client.ProviderClientFactory;
import ru.registry.provider.Provider;
import ru.registry.provider.ProviderSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProviderRegistryTest {

    private static final String PHONE = "79991234567";
    private static final String OK_BODY = "{\"status\":\"ok\"}";
    private static final Provider ALPHA = new Provider("alpha", "https://alpha.example.com", "alpha-key");
    private static final Provider BETA = new Provider("beta", "https://beta.example.com/api", "beta-key");

    private MockRestServiceServer server;
    private ProviderRegistry registry;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        registry = new ProviderRegistry(new ProviderClientFactory(builder, new ObjectMapper()));
        registry.replaceProviders(List.of(ALPHA, BETA));
    }

    @Test
    void returnsTrueWhenProviderRespondsOk() {
        server.expect(requestTo("https://alpha.example.com/subscribers/" + PHONE))
                .andExpect(header(ProviderClientFactory.DEFAULT_API_KEY_HEADER, "alpha-key"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertTrue(registry.isPhoneAvailable("alpha", PHONE));
        server.verify();
    }

    @Test
    void returnsFalseWhenProviderRespondsNotFound() {
        server.expect(requestTo("https://beta.example.com/api/subscribers/" + PHONE))
                .andExpect(header(ProviderClientFactory.DEFAULT_API_KEY_HEADER, "beta-key"))
                .andRespond(withResourceNotFound());

        assertFalse(registry.isPhoneAvailable("beta", PHONE));
        server.verify();
    }

    @Test
    void throwsForUnknownProvider() {
        UnknownProviderException exception = assertThrows(UnknownProviderException.class,
                () -> registry.isPhoneAvailable("gamma", PHONE));
        assertEquals("gamma", exception.providerId());
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> registry.isPhoneAvailable(null, PHONE));
        assertThrows(NullPointerException.class, () -> registry.isPhoneAvailable("alpha", null));
        assertThrows(NullPointerException.class, () -> registry.replaceProviders((List<Provider>) null));
        assertThrows(NullPointerException.class, () -> registry.replaceProviders((ProviderSource) null));
    }

    @Test
    void emptyRegistryRejectsAnyProvider() {
        ProviderRegistry empty = new ProviderRegistry(ProviderClientFactory.withDefaultTimeouts());

        assertEquals(Set.of(), empty.providerIds());
        assertThrows(UnknownProviderException.class, () -> empty.isPhoneAvailable("alpha", PHONE));
    }

    @Test
    void replacesProvidersEntirely() {
        Provider gamma = new Provider("gamma", "https://gamma.example.com", "gamma-key");
        registry.replaceProviders(List.of(gamma));

        assertEquals(Set.of("gamma"), registry.providerIds());
        assertThrows(UnknownProviderException.class, () -> registry.isPhoneAvailable("alpha", PHONE));

        server.expect(requestTo("https://gamma.example.com/subscribers/" + PHONE))
                .andExpect(header(ProviderClientFactory.DEFAULT_API_KEY_HEADER, "gamma-key"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertTrue(registry.isPhoneAvailable("gamma", PHONE));
        server.verify();
    }

    @Test
    void replacesProvidersWithEmptyList() {
        registry.replaceProviders(List.of());

        assertEquals(Set.of(), registry.providerIds());
    }

    @Test
    void replacesProvidersFromSource() {
        registry.replaceProviders(() -> List.of(BETA));

        assertEquals(Set.of("beta"), registry.providerIds());
    }

    @Test
    void returnsSnapshotOfProviderIds() {
        Set<String> before = registry.providerIds();
        registry.replaceProviders(List.of(BETA));

        assertEquals(Set.of("alpha", "beta"), before);
        assertThrows(UnsupportedOperationException.class, () -> registry.providerIds().clear());
    }

    @Test
    void rejectsDuplicateProviderIdsAndKeepsPreviousProviders() {
        Provider duplicate = new Provider("alpha", "https://other.example.com", "other-key");

        assertThrows(IllegalArgumentException.class, () -> registry.replaceProviders(List.of(ALPHA, duplicate)));
        assertEquals(Set.of("alpha", "beta"), registry.providerIds());
    }

    @Test
    void rejectsNullProviderAndKeepsPreviousProviders() {
        assertThrows(NullPointerException.class, () -> registry.replaceProviders(Arrays.asList(ALPHA, null)));
        assertEquals(Set.of("alpha", "beta"), registry.providerIds());
    }

    @Test
    void keepsPreviousProvidersWhenSourceFails() {
        assertThrows(IllegalStateException.class, () -> registry.replaceProviders(() -> {
            throw new IllegalStateException("source failed");
        }));
        assertEquals(Set.of("alpha", "beta"), registry.providerIds());
    }

    @Test
    void validatesFactoryTimeouts() {
        assertThrows(IllegalArgumentException.class,
                () -> ProviderClientFactory.withTimeouts(Duration.ZERO, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> ProviderClientFactory.withTimeouts(Duration.ofSeconds(1), Duration.ofSeconds(-1)));
        assertThrows(NullPointerException.class,
                () -> ProviderClientFactory.withTimeouts(null, Duration.ofSeconds(1)));
    }
}
