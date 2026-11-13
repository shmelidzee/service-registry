package ru.registry.config;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.Objects;

public final class RegistryContext {

    private RegistryContext() {
    }

    public static AnnotationConfigApplicationContext open(String providersFile) {
        Objects.requireNonNull(providersFile, "providersFile must not be null");
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("arguments", Map.of("registry.providers-file", providersFile)));
            context.register(RegistryConfiguration.class);
            context.refresh();
            return context;
        } catch (RuntimeException e) {
            context.close();
            throw e;
        }
    }
}
