package ru.registry.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.registry.client.ProviderClientFactory;
import ru.registry.provider.JsonFileProviderSource;
import ru.registry.service.ProviderRegistry;

import java.nio.file.Path;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public final class RegistryConfiguration {

    @Bean
    public ProviderClientFactory providerClientFactory(
            @Value("${registry.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${registry.read-timeout-ms:10000}") long readTimeoutMs,
            @Value("${registry.api-key-header:" + ProviderClientFactory.DEFAULT_API_KEY_HEADER + "}") String apiKeyHeader) {
        return ProviderClientFactory
                .withTimeouts(Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs))
                .withApiKeyHeader(apiKeyHeader);
    }

    @Bean
    public ProviderRegistry providerRegistry(ProviderClientFactory providerClientFactory,
                                             @Value("${registry.providers-file:}") String providersFile) {
        ProviderRegistry registry = new ProviderRegistry(providerClientFactory);
        if (!providersFile.isBlank()) {
            registry.replaceProviders(new JsonFileProviderSource(Path.of(providersFile)));
        }
        return registry;
    }
}
