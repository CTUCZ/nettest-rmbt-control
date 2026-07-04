package at.rtr.rmbt.service.impl;

import at.rtr.rmbt.enums.IntegrityAction;
import at.rtr.rmbt.enums.IntegrityStatus;
import at.rtr.rmbt.enums.TestPlatform;
import at.rtr.rmbt.properties.IntegrityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decision matrix (design par. 6). Enforcement applies to Android only; other platforms cannot
 * provide Play Integrity and are merely recorded. Note: the platform field is self-reported -
 * spoofing it is a known, accepted residual risk tracked via the persisted records.
 *
 * Only the "regular" enforcement class is implemented: /testRequest carries no certified-measurement
 * indication yet (shared spec par. 5.5 - deferred), so a "certified" class cannot be evaluated.
 */
@Component
@RequiredArgsConstructor
public class IntegrityPolicyService {

    private static final String ENFORCE = "enforce";

    private final IntegrityProperties properties;

    public IntegrityAction decide(TestPlatform platform, IntegrityStatus status, String clientError) {
        if (!ENFORCE.equalsIgnoreCase(properties.getEnforcement().getRegular())) {
            return IntegrityAction.ALLOWED;
        }
        if (platform != TestPlatform.ANDROID) {
            return IntegrityAction.ALLOWED;
        }
        return switch (status) {
            case PASS, UNAVAILABLE -> IntegrityAction.ALLOWED;
            case FAIL -> IntegrityAction.REJECTED;
            case CLIENT_ERROR -> properties.getRejectErrors().contains(clientError)
                    ? IntegrityAction.REJECTED
                    : IntegrityAction.ALLOWED;
            case MISSING -> properties.isRejectMissingFields()
                    ? IntegrityAction.REJECTED
                    : IntegrityAction.ALLOWED;
        };
    }
}
