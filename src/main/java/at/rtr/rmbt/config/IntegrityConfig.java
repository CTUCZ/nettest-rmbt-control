package at.rtr.rmbt.config;

import at.rtr.rmbt.properties.IntegrityProperties;
import at.rtr.rmbt.service.IntegrityVerdictClient;
import at.rtr.rmbt.service.impl.PlayIntegrityVerdictClient;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    /**
     * One startup line stating whether Play Integrity verification is actually active. Without it
     * the feature is indistinguishable from "off" in the logs: a disabled feature logs nothing and
     * a healthy enabled one is also almost silent (evidence goes to test_integrity, not the log).
     * Runs after all singletons so it sees the same state IntegrityService uses at runtime
     * (enabled flag AND presence of the verdict client bean).
     */
    @Bean
    public SmartInitializingSingleton integrityStartupLogger(IntegrityProperties properties,
            ObjectProvider<IntegrityVerdictClient> verdictClientProvider) {
        return () -> {
            if (!properties.isEnabled()) {
                log.info("Play Integrity verification is DISABLED (app.integrity.enabled=false)");
                return;
            }
            if (verdictClientProvider.getIfAvailable() == null) {
                log.warn("Play Integrity verification is OFF: app.integrity.enabled=true but the "
                        + "service-account credentials are missing or unreadable (see ERROR above) - "
                        + "requests are NOT being checked");
                return;
            }
            log.info("Play Integrity verification is ENABLED (package={}, enforcement certified={} regular={}, "
                            + "certificate check {})",
                    properties.getPackageName(),
                    properties.getEnforcement().getCertified(), properties.getEnforcement().getRegular(),
                    properties.getCertificateDigests().isEmpty() ? "SKIPPED - no digests configured" : "active");
        };
    }

    @Bean(destroyMethod = "shutdownNow")
    @ConditionalOnProperty(prefix = "app.integrity", name = "enabled", havingValue = "true")
    public ExecutorService integrityTokenRefreshExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "play-integrity-token-refresh");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.integrity", name = "enabled", havingValue = "true")
    public IntegrityVerdictClient integrityVerdictClient(IntegrityProperties properties,
            ExecutorService integrityTokenRefreshExecutor) {
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
        // google-auth-library-oauth2-http 1.30.1 builds the token-refresh HTTP request itself
        // (ServiceAccountCredentials#refreshAccessToken) and exposes no hook to set a connect/read
        // timeout on it - the HttpTransportFactory only supplies the raw HttpTransport, and
        // NetHttpTransport has no timeout setting either (only proxy/SSL knobs). With no clean
        // per-request timeout available through GoogleCredentials for this version, the refresh is
        // run on a dedicated single-thread executor and the wait is capped with Future#get: a hung
        // oauth2.googleapis.com call can still occupy that background thread, but it can never
        // block the calling request thread past decodeTimeoutMs. A timeout (or any refresh failure)
        // maps to the same IllegalStateException the caller already treats as UNAVAILABLE.
        Supplier<String> accessTokenSupplier = () -> {
            Future<String> future = integrityTokenRefreshExecutor.submit(() -> {
                credentials.refreshIfExpired();
                return credentials.getAccessToken().getTokenValue();
            });
            try {
                return future.get(properties.getDecodeTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new IllegalStateException("Cannot obtain Play Integrity access token", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Cannot obtain Play Integrity access token", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Cannot obtain Play Integrity access token",
                        e.getCause() != null ? e.getCause() : e);
            }
        };
        // Dedicated instance: the shared RestTemplate bean has no timeouts. No retry - a failed
        // call maps to UNAVAILABLE (fail-open by policy). Connect and read each get the full
        // budget, and the token-refresh Future#get above is bounded by the same budget, so the
        // worst case is ~3x decodeTimeoutMs; acceptable because the call runs outside any DB
        // transaction (only client-perceived latency).
        RestTemplate restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofMillis(properties.getDecodeTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getDecodeTimeoutMs()))
                .build();
        return new PlayIntegrityVerdictClient(restTemplate, accessTokenSupplier, properties);
    }
}
