package at.rtr.rmbt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Typed subset of the {@code decodeIntegrityToken} response. Every nested object may be missing:
 * Google omits appIntegrity details and device verdicts when the app is not PLAY_RECOGNIZED or
 * the token was replayed (UNEVALUATED).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayIntegrityDecodeResponse {

    private Verdict tokenPayloadExternal;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Verdict {
        private RequestDetails requestDetails;
        private AppIntegrity appIntegrity;
        private DeviceIntegrity deviceIntegrity;
        private AccountDetails accountDetails;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RequestDetails {
        private String requestPackageName;
        private String requestHash;
        /** int64 serialized as JSON string by Google. */
        private String timestampMillis;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppIntegrity {
        private String appRecognitionVerdict;
        private String packageName;
        /** Base64 web-safe (no padding) digests; only populated when the app is recognized. */
        private List<String> certificateSha256Digest;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeviceIntegrity {
        private List<String> deviceRecognitionVerdict;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccountDetails {
        private String appLicensingVerdict;
    }
}
