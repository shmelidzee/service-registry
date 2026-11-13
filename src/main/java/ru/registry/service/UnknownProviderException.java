package ru.registry.service;

public final class UnknownProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String providerId;

    public UnknownProviderException(String providerId) {
        super("Unknown provider: " + providerId);
        this.providerId = providerId;
    }

    public String providerId() {
        return providerId;
    }
}
