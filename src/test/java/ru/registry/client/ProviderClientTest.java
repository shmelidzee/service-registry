package ru.registry.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.registry.provider.Provider;

import java.net.SocketTimeoutException;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProviderClientTest {

    private static final String PHONE = "79991234567";
    private static final String URL = "https://alpha.example.com/api/subscribers/" + PHONE;
    private static final String OK_BODY = "{\"status\":\"ok\"}";

    private MockRestServiceServer server;
    private ProviderClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ProviderClientFactory factory = new ProviderClientFactory(builder, new ObjectMapper());
        client = factory.create(new Provider("alpha", "https://alpha.example.com/api", "alpha-key"));
    }

    @Test
    void returnsTrueWhenProviderRespondsOk() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(ProviderClientFactory.DEFAULT_API_KEY_HEADER, "alpha-key"))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertTrue(client.isPhoneAvailable(PHONE));
        server.verify();
    }

    @Test
    void returnsFalseWhenProviderRespondsNotFound() {
        expectPhone().andRespond(withResourceNotFound());

        assertFalse(client.isPhoneAvailable(PHONE));
        server.verify();
    }

    @Test
    void returnsTrueWhenBodyHasExtraFields() {
        expectPhone().andRespond(withSuccess("{\"status\":\"ok\",\"operator\":\"x\"}", MediaType.APPLICATION_JSON));

        assertTrue(client.isPhoneAvailable(PHONE));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":\"blocked\"}", "{\"status\":\"OK\"}", "{}", "null", "", "  "})
    void returnsFalseWhenBodyIsNotOkStatus(String body) {
        expectPhone().andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON).body(body));

        assertFalse(client.isPhoneAvailable(PHONE));
        server.verify();
    }

    @Test
    void readsOkStatusRegardlessOfContentType() {
        expectPhone().andRespond(withSuccess(OK_BODY, MediaType.TEXT_PLAIN));
        expectPhone().andRespond(withSuccess().body(OK_BODY));

        assertTrue(client.isPhoneAvailable(PHONE));
        assertTrue(client.isPhoneAvailable(PHONE));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<html>ok</html>",
            "[]",
            "\"ok\"",
            "{\"status\":\"ok\"} garbage",
            "{\"status\":\"ok\"}{\"status\":\"ok\"}",
            "{\"status\":\"bad\",\"status\":\"ok\"}"
    })
    void failsWhenBodyIsNotStatusObject(String body) {
        expectPhone().andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThrows(RestClientException.class, () -> client.isPhoneAvailable(PHONE));
        server.verify();
    }

    @Test
    void failsWhenBodyIsTooLarge() {
        String body = "{\"status\":\"ok\",\"padding\":\"" + "x".repeat(ProviderClient.MAX_BODY_BYTES) + "\"}";
        expectPhone().andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThrows(RestClientException.class, () -> client.isPhoneAvailable(PHONE));
        server.verify();
    }

    @Test
    void propagatesServerError() {
        expectPhone().andRespond(withServerError().body("boom"));

        HttpServerErrorException exception = assertThrows(HttpServerErrorException.class,
                () -> client.isPhoneAvailable(PHONE));
        assertInstanceOf(HttpServerErrorException.InternalServerError.class, exception);
        assertEquals("boom", exception.getResponseBodyAsString());
        server.verify();
    }

    @Test
    void decodesErrorBodyWithCharsetFromContentType() {
        Charset windows1251 = Charset.forName("windows-1251");
        expectPhone().andRespond(withServerError()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, windows1251))
                .body("ошибка".getBytes(windows1251)));

        HttpServerErrorException exception = assertThrows(HttpServerErrorException.class,
                () -> client.isPhoneAvailable(PHONE));
        assertEquals("ошибка", exception.getResponseBodyAsString());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 409, 429})
    void propagatesClientErrorOtherThanNotFound(int status) {
        expectPhone().andRespond(withStatus(HttpStatus.valueOf(status)));

        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class,
                () -> client.isPhoneAvailable(PHONE));
        assertEquals(status, exception.getStatusCode().value());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 202, 204, 301, 302, 304})
    void failsOnUnexpectedNonErrorStatus(int status) {
        expectPhone().andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON)
                .body(OK_BODY));

        RestClientResponseException exception = assertThrows(RestClientResponseException.class,
                () -> client.isPhoneAvailable(PHONE));
        assertEquals(status, exception.getStatusCode().value());
        server.verify();
    }

    @Test
    void propagatesNetworkError() {
        expectPhone().andRespond(withException(new SocketTimeoutException("timeout")));

        assertThrows(ResourceAccessException.class, () -> client.isPhoneAvailable(PHONE));
        server.verify();
    }

    @Test
    void encodesPhoneInPath() {
        server.expect(requestTo("https://alpha.example.com/api/subscribers/%2B79991234567"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertTrue(client.isPhoneAvailable("+79991234567"));
        server.verify();
    }

    @Test
    void keepsEncodedCharactersOfBaseUrlAsIs() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer encodedServer = MockRestServiceServer.bindTo(builder).build();
        ProviderClient encodedClient = new ProviderClientFactory(builder, new ObjectMapper())
                .create(new Provider("alpha", "https://alpha.example.com/a%20b/", "alpha-key"));
        encodedServer.expect(requestTo("https://alpha.example.com/a%20b/subscribers/%2B79991234567"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertTrue(encodedClient.isPhoneAvailable("+79991234567"));
        encodedServer.verify();
    }

    @Test
    void sendsApiKeyInConfiguredHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer customServer = MockRestServiceServer.bindTo(builder).build();
        ProviderClient customClient = new ProviderClientFactory(builder, new ObjectMapper())
                .withApiKeyHeader("Authorization")
                .create(new Provider("alpha", "https://alpha.example.com/api", "alpha-key"));
        customServer.expect(requestTo(URL))
                .andExpect(header("Authorization", "alpha-key"))
                .andExpect(headerDoesNotExist(ProviderClientFactory.DEFAULT_API_KEY_HEADER))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertTrue(customClient.isPhoneAvailable(PHONE));
        customServer.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "X Api Key", "X-Api-Key:", "Ключ", "X-Api\r\nKey"})
    void rejectsInvalidApiKeyHeaderName(String headerName) {
        ProviderClientFactory factory = ProviderClientFactory.withDefaultTimeouts();

        assertThrows(IllegalArgumentException.class, () -> factory.withApiKeyHeader(headerName));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Authorization", "X-Api-Key", "x_api.key~2", "!#$%&'*+-.^_`|~"})
    void acceptsValidApiKeyHeaderName(String headerName) {
        ProviderClientFactory factory = ProviderClientFactory.withDefaultTimeouts();

        assertEquals(headerName, factory.withApiKeyHeader(headerName).apiKeyHeader());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "+1", "123456789012345", "+123456789012345"})
    void acceptsValidPhone(String phone) {
        expectPhone(phone).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertTrue(client.isPhoneAvailable(phone));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", " ", "+", "..", "123/456", "7999 123", "abc", "++7999", "7+999",
            "1234567890123456", "+1234567890123456", "7999\n", "١٢٣"
    })
    void rejectsInvalidPhoneWithoutRequest(String phone) {
        assertThrows(IllegalArgumentException.class, () -> client.isPhoneAvailable(phone));
        server.verify();
    }

    @Test
    void rejectsNullPhone() {
        assertThrows(NullPointerException.class, () -> client.isPhoneAvailable(null));
    }

    private ResponseActions expectPhone(String phone) {
        String encoded = phone.startsWith("+") ? "%2B" + phone.substring(1) : phone;
        return server.expect(requestTo("https://alpha.example.com/api/subscribers/" + encoded));
    }

    private ResponseActions expectPhone() {
        return server.expect(requestTo(URL));
    }
}
