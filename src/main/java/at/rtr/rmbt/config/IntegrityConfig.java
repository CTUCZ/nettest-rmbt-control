package at.rtr.rmbt.config;

import at.rtr.rmbt.properties.IntegrityProperties;
import at.rtr.rmbt.service.IntegrityVerdictClient;
import at.rtr.rmbt.service.impl.PlayIntegrityVerdictClient;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Wiring of the Play Integrity decode client. The application must ALWAYS start: the bean only
 * exists when the feature is enabled AND usable credentials are configured (design par. 7 -
 * "enabled: false or missing credentials-file => feature off"). Misconfiguration is reported
 * with an ERROR log, never a startup crash. Returning null from the @Bean method means the bean
 * is simply absent; IntegrityService detects that via ObjectProvider and treats the feature as off.
 */
@Slf4j
@Configuration
public class IntegrityConfig {

    private static final String PLAY_INTEGRITY_SCOPE = "https://www.googleapis.com/auth/playintegrity";

    @Bean
    @ConditionalOnProperty(prefix = "app.integrity", name = "enabled", havingValue = "true")
    public IntegrityVerdictClient integrityVerdictClient(IntegrityProperties properties) {
        if (org.apache.commons.lang3.StringUtils.isBlank(properties.getCredentialsFile())) {
            log.error("app.integrity.enabled=true but credentials-file is empty - integrity feature stays OFF");
            return null;
        }
        GoogleCredentials credentials;
        try (FileInputStream stream = new FileInputStream(properties.getCredentialsFile())) {
            credentials = GoogleCredentials.fromStream(stream).createScoped(PLAY_INTEGRITY_SCOPE);
        } catch (IOException e) {
            log.error("app.integrity credentials unreadable ({}) - integrity feature stays OFF: {}",
                    properties.getCredentialsFile(), e.getMessage());
            return null;
        }
        Supplier<String> accessTokenSupplier = () -> {
            try {
                credentials.refreshIfExpired();
                return credentials.getAccessToken().getTokenValue();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot obtain Play Integrity access token", e);
            }
        };
        // Dedicated instance: the shared RestTemplate bean has no timeouts. No retry - a failed
        // call maps to UNAVAILABLE (fail-open by policy). Connect and read each get the full
        // budget, so the worst case is ~2x decodeTimeoutMs; acceptable because the call runs
        // outside any DB transaction (only client-perceived latency).
        RestTemplate restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofMillis(properties.getDecodeTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getDecodeTimeoutMs()))
                .build();
        return new PlayIntegrityVerdictClient(restTemplate, accessTokenSupplier, properties);
    }
}
