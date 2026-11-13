package ru.registry.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import ru.registry.provider.Provider;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

public final class ProviderClientFactory {

    public static final String DEFAULT_API_KEY_HEADER = "X-Api-Key";
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);

    private static final String HEADER_NAME_SYMBOLS = "!#$%&'*+-.^_`|~";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final String apiKeyHeader;

    public ProviderClientFactory(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this(restClientBuilder, objectMapper, DEFAULT_API_KEY_HEADER);
    }

    private ProviderClientFactory(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, String apiKeyHeader) {
        this.restClientBuilder = Objects.requireNonNull(restClientBuilder, "restClientBuilder must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.apiKeyHeader = requireHeaderName(apiKeyHeader);
    }

    public static ProviderClientFactory withDefaultTimeouts() {
        return withTimeouts(DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public static ProviderClientFactory withTimeouts(Duration connectTimeout, Duration readTimeout) {
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new ProviderClientFactory(RestClient.builder().requestFactory(requestFactory), new ObjectMapper());
    }

    public ProviderClientFactory withApiKeyHeader(String headerName) {
        return new ProviderClientFactory(restClientBuilder, objectMapper, headerName);
    }

    public String apiKeyHeader() {
        return apiKeyHeader;
    }

    public ProviderClient create(Provider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(provider.baseUrl());
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        RestClient restClient = restClientBuilder.clone()
                .uriBuilderFactory(uriBuilderFactory)
                .defaultHeader(apiKeyHeader, provider.apiKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new ProviderClient(provider.id(), restClient, objectMapper);
    }

    private static String requireHeaderName(String headerName) {
        Objects.requireNonNull(headerName, "apiKeyHeader must not be null");
        if (headerName.isEmpty() || !headerName.chars().allMatch(ProviderClientFactory::isHeaderNameChar)) {
            throw new IllegalArgumentException("Invalid header name: " + headerName);
        }
        return headerName;
    }

    private static boolean isHeaderNameChar(int c) {
        boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        boolean digit = c >= '0' && c <= '9';
        return letter || digit || HEADER_NAME_SYMBOLS.indexOf(c) >= 0;
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
