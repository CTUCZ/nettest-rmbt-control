package at.rtr.rmbt.service.impl;

import at.rtr.rmbt.enums.IntegrityAction;
import at.rtr.rmbt.enums.IntegrityStatus;
import at.rtr.rmbt.enums.TestPlatform;
import at.rtr.rmbt.properties.IntegrityProperties;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IntegrityPolicyServiceTest {

    private IntegrityProperties properties;
    private IntegrityPolicyService policy;

    @Before
    public void setUp() {
        properties = new IntegrityProperties();
        policy = new IntegrityPolicyService(properties);
    }

    private void enforce() {
        properties.getEnforcement().setRegular("enforce");
    }

    @Test
    public void decide_whenMonitorMode_expectAlwaysAllowed() {
        // Given: default monitor mode (Phase 1) - even a failed verdict passes
        assertEquals(IntegrityAction.ALLOWED, policy.decide(TestPlatform.ANDROID, IntegrityStatus.FAIL, null));
        assertEquals(IntegrityAction.ALLOWED, policy.decide(TestPlatform.ANDROID, IntegrityStatus.CLIENT_ERROR, "NOT_AVAILABLE"));
        assertEquals(IntegrityAction.ALLOWED, policy.decide(TestPlatform.ANDROID, IntegrityStatus.MISSING, null));
    }

    @Test
    public void decide_whenEnforceAndVerdictFails_expectRejected() {
        // Given
        enforce();

        // When / Then
        assertEquals(IntegrityAction.REJECTED, policy.decide(TestPlatform.ANDROID, IntegrityStatus.FAIL, null));
    }

    @Test
    public void decide_whenEnforceAndPass_expectAllowed() {
        enforce();
        assertEquals(IntegrityAction.ALLOWED, policy.decide(TestPlatform.ANDROID, IntegrityStatus.PASS, null));
    }

    @Test
    public void decide_whenEnforceAndUnavailable_expectAllowed() {
        // Given: Google outage must not break measurements (fail-open)
        enforce();
        assertEquals(IntegrityAction.ALLOWED, policy.decide(TestPlatform.ANDROID, IntegrityStatus.UNAVAILABLE, null));
    }

    @Test
    public void decide_whenEnforceAndClientErrorNotAvailable_expectRejected() {
        // Given: NOT_AVAILABLE is in reject-errors by default (device cannot attest at all)
        enforce();
        assertEquals(IntegrityAction.REJECTED, policy.decide(TestPlatform.ANDROID, IntegrityStatus.CLIENT_ERROR, "NOT_AVAILABLE"));
    }

    @Test
    public void decide_whenEnforceAndClientErrorTimeout_expectAllowed() {
        // Given: TIMEOUT/REQUEST_FAILED/PREPARE_FAILED occur on legitimate filtered networks
        enforce();
        assertEquals(IntegrityAction.ALLOWED, policy.decide(TestPlatform.ANDROID, IntegrityStatus.CLIENT_ERROR, "TIMEOUT"));
        assertEquals(IntegrityAction.ALLOWED, policy.decide(TestPlatform.ANDROID, IntegrityStatus.CLIENT_ERROR, "REQUEST_FAILED"));
    }

    @Test
    public void decide_whenEnforceAndMissingFieldsDefault_expectAllowed() {
        // Given: reject-missing-fields=false until new app adoption is sufficient
        enforce();
        assertEquals(IntegrityAction.ALLOWED, policy.decide(TestPlatform.ANDROID, IntegrityStatus.MISSING, null));
    }

    @Test
    public void decide_whenEnforceAndMissingFieldsEnabled_expectRejected() {
        // Given
        enforce();
        properties.setRejectMissingFields(true);

        // When / Then
        assertEquals(IntegrityAction.REJECTED, policy.decide(TestPlatform.ANDROID, IntegrityStatus.MISSING, null));
    }

    @Test
    public void decide_whenNonAndroidPlatform_expectAlwaysAllowed() {
        // Given: iOS/web/CLI cannot provide Play Integrity - never enforced (design par. 6)
        enforce();
        properties.setRejectMissingFields(true);
        assertEquals(IntegrityAction.ALLOWED, policy.decide(TestPlatform.IOS, IntegrityStatus.MISSING, null));
        assertEquals(IntegrityAction.ALLOWED, policy.decide(null, IntegrityStatus.MISSING, null));
        // even a failed verdict from a non-Android platform claim is allowed (only marked)
        assertEquals(IntegrityAction.ALLOWED, policy.decide(TestPlatform.IOS, IntegrityStatus.FAIL, null));
    }
}
