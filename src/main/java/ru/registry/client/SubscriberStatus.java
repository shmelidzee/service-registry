package ru.registry.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record SubscriberStatus(String status) {

    private static final String OK = "ok";

    boolean isOk() {
        return OK.equals(status);
    }
}
