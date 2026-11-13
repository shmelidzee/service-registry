package ru.registry.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.registry.client.ProviderClient;
import ru.registry.client.ProviderClientFactory;
import ru.registry.provider.Provider;
import ru.registry.provider.ProviderSource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ProviderRegistry {

    private static final Log log = LogFactory.getLog(ProviderRegistry.class);

    private final ProviderClientFactory clientFactory;
    private volatile Map<String, ProviderClient> clients = Map.of();

    public ProviderRegistry(ProviderClientFactory clientFactory) {
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory must not be null");
    }

    public void replaceProviders(List<Provider> providers) {
        Objects.requireNonNull(providers, "providers must not be null");
        Map<String, ProviderClient> newClients = new LinkedHashMap<>();
        for (Provider provider : providers) {
            Objects.requireNonNull(provider, "provider must not be null");
            if (newClients.containsKey(provider.id())) {
                throw new IllegalArgumentException("Duplicate provider id: " + provider.id());
            }
            newClients.put(provider.id(), clientFactory.create(provider));
        }
        clients = Collections.unmodifiableMap(newClients);
        log.info("Provider set replaced: " + newClients.keySet());
    }

    public void replaceProviders(ProviderSource source) {
        Objects.requireNonNull(source, "source must not be null");
        replaceProviders(source.providers());
    }

    public Set<String> providerIds() {
        return clients.keySet();
    }

    public boolean isPhoneAvailable(String providerId, String phone) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        ProviderClient client = clients.get(providerId);
        if (client == null) {
            throw new UnknownProviderException(providerId);
        }
        return client.isPhoneAvailable(phone);
    }
}
