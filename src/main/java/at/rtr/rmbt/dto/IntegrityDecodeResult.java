package at.rtr.rmbt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntegrityDecodeResult {

    public enum Outcome {
        /** Verdict decoded successfully. */
        OK,
        /** HTTP 4xx other than 429 - the token itself is invalid (treated as a failed verdict). */
        INVALID_TOKEN,
        /** HTTP 429 - decode quota exhausted (monitored separately: potential quota-bypass attack). */
        QUOTA_EXCEEDED,
        /** HTTP 5xx, network error or timeout - Google unreachable (fail-open). */
        UNAVAILABLE
    }

    private final Outcome outcome;
    private final PlayIntegrityDecodeResponse.Verdict verdict;
    private final long latencyMs;
}
