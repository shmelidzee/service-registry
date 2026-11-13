package ru.registry.provider;

import java.util.List;

@FunctionalInterface
public interface ProviderSource {

    List<Provider> providers();
}
