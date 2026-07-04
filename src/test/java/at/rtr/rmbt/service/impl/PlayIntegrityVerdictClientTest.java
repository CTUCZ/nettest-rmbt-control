package at.rtr.rmbt.service.impl;

import at.rtr.rmbt.dto.IntegrityDecodeResult;
import at.rtr.rmbt.properties.IntegrityProperties;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class PlayIntegrityVerdictClientTest {

    private static final String DECODE_URL =
            "https://playintegrity.googleapis.com/v1/cz.ctu.rmbt.android.prod:decodeIntegrityToken";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private PlayIntegrityVerdictClient client;

    @Before
    public void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        IntegrityProperties properties = new IntegrityProperties();
        client = new PlayIntegrityVerdictClient(restTemplate, () -> "test-access-token", properties);
    }

    @Test
    public void decode_whenGoogleReturnsVerdict_expectOkWithParsedVerdict() throws IOException {
        // Given
        String body = new String(getClass().getResourceAsStream("/integrity/verdict-pass.json").readAllBytes(),
                StandardCharsets.UTF_8);
        server.expect(requestTo(DECODE_URL))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-access-token"))
                .andExpect(jsonPath("$.integrityToken").value("the-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        // When
        IntegrityDecodeResult result = client.decode("the-token");

        // Then
        assertEquals(IntegrityDecodeResult.Outcome.OK, result.getOutcome());
        assertNotNull(result.getVerdict());
        assertEquals("PLAY_RECOGNIZED", result.getVerdict().getAppIntegrity().getAppRecognitionVerdict());
        server.verify();
    }

    @Test
    public void decode_whenGoogleReturns400_expectInvalidToken() {
        // Given: Google answers 400 INVALID_ARGUMENT for garbage/undecryptable tokens
        server.expect(requestTo(DECODE_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        // When
        IntegrityDecodeResult result = client.decode("garbage");

        // Then: must NOT map to UNAVAILABLE (would create an enforcement bypass)
        assertEquals(IntegrityDecodeResult.Outcome.INVALID_TOKEN, result.getOutcome());
        assertNull(result.getVerdict());
    }

    @Test
    public void decode_whenGoogleReturns429_expectQuotaExceeded() {
        // Given
        server.expect(requestTo(DECODE_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // When / Then
        assertEquals(IntegrityDecodeResult.Outcome.QUOTA_EXCEEDED, client.decode("t").getOutcome());
    }

    @Test
    public void decode_whenGoogleReturns403_expectUnavailable() {
        // Given: 401/403 means OUR service-account credentials are broken, not the client's token -
        // must map to fail-open UNAVAILABLE, otherwise enforce mode rejects every Android measurement
        server.expect(requestTo(DECODE_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        // When / Then
        assertEquals(IntegrityDecodeResult.Outcome.UNAVAILABLE, client.decode("t").getOutcome());
    }

    @Test
    public void decode_whenGoogleReturns503_expectUnavailable() {
        // Given
        server.expect(requestTo(DECODE_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // When / Then
        assertEquals(IntegrityDecodeResult.Outcome.UNAVAILABLE, client.decode("t").getOutcome());
    }

    @Test
    public void decode_whenNetworkErrorOrTimeout_expectUnavailable() {
        // Given
        server.expect(requestTo(DECODE_URL)).andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

        // When / Then
        assertEquals(IntegrityDecodeResult.Outcome.UNAVAILABLE, client.decode("t").getOutcome());
    }

    @Test
    public void decode_whenAccessTokenSupplierFails_expectUnavailable() {
        // Given: credentials refresh failure must not break /testRequest
        client = new PlayIntegrityVerdictClient(restTemplate,
                () -> { throw new IllegalStateException("no credentials"); }, new IntegrityProperties());

        // When / Then
        assertEquals(IntegrityDecodeResult.Outcome.UNAVAILABLE, client.decode("t").getOutcome());
    }
}
