package at.rtr.rmbt.service.impl;

import at.rtr.rmbt.dto.IntegrityDecodeResult;
import at.rtr.rmbt.dto.PlayIntegrityDecodeResponse;
import at.rtr.rmbt.properties.IntegrityProperties;
import at.rtr.rmbt.service.IntegrityVerdictClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class PlayIntegrityVerdictClient implements IntegrityVerdictClient {

    private final RestTemplate restTemplate;
    private final Supplier<String> accessTokenSupplier;
    private final IntegrityProperties properties;

    @Override
    public IntegrityDecodeResult decode(String integrityToken) {
        long start = System.nanoTime();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessTokenSupplier.get());

            String url = String.format("%s/v1/%s:decodeIntegrityToken",
                    properties.getGoogleApiBaseUrl(), properties.getPackageName());
            HttpEntity<Map<String, String>> request =
                    new HttpEntity<>(Map.of("integrityToken", integrityToken), headers);

            PlayIntegrityDecodeResponse response =
                    restTemplate.postForObject(url, request, PlayIntegrityDecodeResponse.class);

            PlayIntegrityDecodeResponse.Verdict verdict =
                    response == null ? null : response.getTokenPayloadExternal();
            if (verdict == null) {
                log.warn("decodeIntegrityToken returned no tokenPayloadExternal");
                return result(IntegrityDecodeResult.Outcome.INVALID_TOKEN, null, start);
            }
            return result(IntegrityDecodeResult.Outcome.OK, verdict, start);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("decodeIntegrityToken quota exceeded (HTTP 429)");
                return result(IntegrityDecodeResult.Outcome.QUOTA_EXCEEDED, null, start);
            }
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                // OUR credentials problem (expired/revoked service account), not the client's token -
                // in enforce mode this must never fail-close every Android measurement
                log.error("decodeIntegrityToken auth failure (HTTP {}) - check the service-account credentials",
                        e.getStatusCode().value());
                return result(IntegrityDecodeResult.Outcome.UNAVAILABLE, null, start);
            }
            // Other 4xx = the submitted token is invalid; must not fall back to fail-open UNAVAILABLE
            log.info("decodeIntegrityToken rejected the token: HTTP {}", e.getStatusCode().value());
            return result(IntegrityDecodeResult.Outcome.INVALID_TOKEN, null, start);
        } catch (RestClientException e) {
            log.warn("decodeIntegrityToken unavailable: {}", e.getMessage());
            return result(IntegrityDecodeResult.Outcome.UNAVAILABLE, null, start);
        } catch (RuntimeException e) {
            log.warn("decodeIntegrityToken failed before the call (credentials?): {}", e.getMessage());
            return result(IntegrityDecodeResult.Outcome.UNAVAILABLE, null, start);
        }
    }

    private IntegrityDecodeResult result(IntegrityDecodeResult.Outcome outcome,
                                         PlayIntegrityDecodeResponse.Verdict verdict, long startNanos) {
        return IntegrityDecodeResult.builder()
                .outcome(outcome)
                .verdict(verdict)
                .latencyMs((System.nanoTime() - startNanos) / 1_000_000)
                .build();
    }
}
