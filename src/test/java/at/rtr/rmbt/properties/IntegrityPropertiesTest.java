package at.rtr.rmbt.properties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IntegrityPropertiesTest {

    @Test
    public void defaults_expectSafeDisabledMonitorConfiguration() {
        // Given / When: fresh properties (no YAML overrides)
        IntegrityProperties properties = new IntegrityProperties();

        // Then: feature off, monitor-only, spec defaults
        assertFalse(properties.isEnabled());
        assertEquals("cz.ctu.rmbt.android.prod", properties.getPackageName());
        assertEquals("https://playintegrity.googleapis.com", properties.getGoogleApiBaseUrl());
        assertEquals(3000, properties.getDecodeTimeoutMs());
        assertEquals(300_000L, properties.getFreshnessWindowMs());
        assertEquals(20_480, properties.getMaxTokenBytes());
        assertEquals("monitor", properties.getEnforcement().getRegular());
        assertEquals("monitor", properties.getEnforcement().getCertified());
        assertTrue(properties.getRejectErrors().contains("NOT_AVAILABLE"));
        assertFalse(properties.isRejectMissingFields());
        assertTrue(properties.getCertificateDigests().isEmpty());
    }
}
