package at.rtr.rmbt.properties;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Play Integrity verification configuration ({@code app.integrity.*}). Disabled by default;
 * production enables it via deployment overrides together with the service-account credentials.
 */
@Getter
@Setter
@ConfigurationProperties("app.integrity")
public class IntegrityProperties {

    /** Master switch. When false, /testRequest behaves exactly as before this feature. */
    private boolean enabled = false;

    /** Android applicationId; identical for all build types of the CTU fork. */
    private String packageName = "cz.ctu.rmbt.android.prod";

    /**
     * Accepted app-signing certificate digests: base64 web-safe without padding, digest of the
     * Google-managed app signing key (Play Console -> App integrity). Empty list = check skipped
     * (bootstrap before the digest is delivered) - a warning is logged per evaluation.
     */
    private List<String> certificateDigests = new ArrayList<>();

    /** Path to the service-account JSON. Required when enabled=true. */
    private String credentialsFile;

    private String googleApiBaseUrl = "https://playintegrity.googleapis.com";

    /** Connect+read timeout of the decodeIntegrityToken call. No retry: failure maps to UNAVAILABLE. */
    private int decodeTimeoutMs = 3000;

    /** Freshness window for Google's requestDetails.timestampMillis (own policy, not a Google rule). */
    private long freshnessWindowMs = 300_000;

    /** Tokens larger than this are refused without calling Google (quota/DoS protection). */
    private int maxTokenBytes = 20_480;

    private Enforcement enforcement = new Enforcement();

    /** integrity_error values that lead to rejection in enforce mode. */
    private List<String> rejectErrors = new ArrayList<>(List.of("NOT_AVAILABLE"));

    /** Reject Android requests without any integrity fields (old app versions). Enable only after adoption. */
    private boolean rejectMissingFields = false;

    /**
     * Fail fast on a misconfigured security switch rather than silently falling back to monitor
     * mode: the policy decision service treats anything other than exactly "enforce"
     * (case-insensitive) as monitor, so a deployment typo such as "enforced" or "true" would
     * otherwise disable enforcement without any indication. Defaults are always valid, so the
     * application still starts with no overrides at all; only an explicit, wrong override fails
     * startup.
     */
    @PostConstruct
    public void validate() {
        validateEnforcementValue("regular", enforcement.getRegular());
        validateEnforcementValue("certified", enforcement.getCertified());
        // Fix B4: an empty packageName reaches deep into IntegrityVerdictEvaluator's package-name
        // comparison and would NPE there instead of failing fast here at startup.
        if (enabled && StringUtils.isBlank(packageName)) {
            throw new IllegalStateException(
                    "app.integrity.package-name must not be blank when app.integrity.enabled=true");
        }
    }

    private void validateEnforcementValue(String fieldName, String value) {
        if (!"monitor".equalsIgnoreCase(value) && !"enforce".equalsIgnoreCase(value)) {
            throw new IllegalStateException("app.integrity.enforcement." + fieldName
                    + " must be exactly \"monitor\" or \"enforce\" (case-insensitive), but was: " + value);
        }
    }

    @Getter
    @Setter
    public static class Enforcement {

        /**
         * monitor | enforce. INACTIVE until the app sends a cert indication in /testRequest
         * (shared spec par. 5.5 - deferred); kept for forward compatibility.
         */
        private String certified = "monitor";

        /** monitor | enforce; applies to all Android measurements. */
        private String regular = "monitor";
    }
}
