package at.rtr.rmbt.filter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApiLoggingFilterTest {

    @Test
    public void redactIntegrityToken_whenTokenPresent_expectTruncatedValue() {
        // Given: several-KB opaque token must not be logged verbatim (log volume + replay hygiene)
        String longToken = "A".repeat(3000);
        String body = "{\"uuid\":\"abc\",\"integrity_token\":\"" + longToken + "\",\"language\":\"en\"}";

        // When
        String redacted = ApiLoggingFilter.redactIntegrityToken(body);

        // Then: prefix kept for correlation, rest replaced
        assertTrue(redacted.contains("\"integrity_token\":\"" + "A".repeat(24) + "...[REDACTED]\""));
        assertFalse(redacted.contains(longToken));
        assertTrue(redacted.contains("\"language\":\"en\""));
    }

    @Test
    public void redactIntegrityToken_whenNoToken_expectUnchanged() {
        // Given
        String body = "{\"uuid\":\"abc\",\"language\":\"en\"}";

        // When / Then
        assertEquals(body, ApiLoggingFilter.redactIntegrityToken(body));
    }

    @Test
    public void redactIntegrityToken_whenNull_expectNull() {
        assertEquals(null, ApiLoggingFilter.redactIntegrityToken(null));
    }

    @Test
    public void redactIntegrityToken_whenShortToken_expectPrefixKeptAndRedactedMarker() {
        // Given: token shorter than the kept 24-char prefix stays visible as the prefix,
        // only the marker is appended (real tokens are KB-sized, so this is irrelevant in practice)
        String body = "{\"integrity_token\":\"short\"}";

        // When
        String redacted = ApiLoggingFilter.redactIntegrityToken(body);

        // Then
        assertFalse(redacted.contains("\"short\""));
        assertTrue(redacted.contains("[REDACTED]"));
    }
}
