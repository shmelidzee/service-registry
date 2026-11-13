package ru.registry.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.registry.json.JsonReaders;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ProviderClient {

    static final int MAX_BODY_BYTES = 64 * 1024;

    private static final int MAX_PHONE_DIGITS = 15;

    private final String providerId;
    private final RestClient restClient;
    private final ObjectReader statusReader;

    ProviderClient(String providerId, RestClient restClient, ObjectMapper objectMapper) {
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.statusReader = JsonReaders.strict(
                Objects.requireNonNull(objectMapper, "objectMapper must not be null").readerFor(SubscriberStatus.class));
    }

    public boolean isPhoneAvailable(String phone) {
        requireValidPhone(phone);
        return Boolean.TRUE.equals(restClient.get()
                .uri("/subscribers/{phone}", phone)
                .exchange((request, response) -> handle(response)));
    }

    private boolean handle(ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return false;
        }
        byte[] body = readBody(response);
        if (status.isSameCodeAs(HttpStatus.OK)) {
            return isOk(body);
        }
        throw errorFor(response, status, body);
    }

    private byte[] readBody(ClientHttpResponse response) throws IOException {
        try (InputStream stream = response.getBody()) {
            byte[] body = stream.readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw new RestClientException(
                        "Response from provider " + providerId + " exceeds " + MAX_BODY_BYTES + " bytes");
            }
            return body;
        }
    }

    private boolean isOk(byte[] body) {
        if (isBlank(body)) {
            return false;
        }
        try {
            SubscriberStatus status = statusReader.readValue(body);
            return status != null && status.isOk();
        } catch (IOException e) {
            throw new RestClientException("Invalid response from provider " + providerId, e);
        }
    }

    private RestClientResponseException errorFor(ClientHttpResponse response, HttpStatusCode status, byte[] body)
            throws IOException {
        String message = "Provider " + providerId + " responded with status " + status.value();
        String statusText = response.getStatusText();
        HttpHeaders headers = response.getHeaders();
        Charset charset = charsetOf(headers);
        if (status.is4xxClientError()) {
            return HttpClientErrorException.create(message, status, statusText, headers, body, charset);
        }
        if (status.is5xxServerError()) {
            return HttpServerErrorException.create(message, status, statusText, headers, body, charset);
        }
        return new RestClientResponseException(message, status, statusText, headers, body, charset);
    }

    private static Charset charsetOf(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType == null || contentType.getCharset() == null) {
            return StandardCharsets.UTF_8;
        }
        return contentType.getCharset();
    }

    private static boolean isBlank(byte[] body) {
        for (byte b : body) {
            if (b != ' ' && b != '\t' && b != '\n' && b != '\r') {
                return false;
            }
        }
        return true;
    }

    private static void requireValidPhone(String phone) {
        Objects.requireNonNull(phone, "phone must not be null");
        String digits = phone.startsWith("+") ? phone.substring(1) : phone;
        if (digits.isEmpty() || digits.length() > MAX_PHONE_DIGITS || !isAsciiDigits(digits)) {
            throw new IllegalArgumentException("Invalid phone number: " + phone);
        }
    }

    private static boolean isAsciiDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
