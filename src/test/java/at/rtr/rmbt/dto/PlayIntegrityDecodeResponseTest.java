package at.rtr.rmbt.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PlayIntegrityDecodeResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void deserialize_whenFullVerdict_expectAllFieldsTyped() throws Exception {
        // Given
        InputStream json = getClass().getResourceAsStream("/integrity/verdict-pass.json");

        // When
        PlayIntegrityDecodeResponse response = mapper.readValue(json, PlayIntegrityDecodeResponse.class);

        // Then
        PlayIntegrityDecodeResponse.Verdict verdict = response.getTokenPayloadExternal();
        assertEquals("cz.ctu.rmbt.android.prod", verdict.getRequestDetails().getRequestPackageName());
        assertEquals("PLACEHOLDER_HASH", verdict.getRequestDetails().getRequestHash());
        // timestampMillis is a JSON string in Google's response
        assertEquals("1719912345678", verdict.getRequestDetails().getTimestampMillis());
        assertEquals("PLAY_RECOGNIZED", verdict.getAppIntegrity().getAppRecognitionVerdict());
        assertEquals(List.of("6a6a1474b5cbbb2b1aa57e0bc3"), verdict.getAppIntegrity().getCertificateSha256Digest());
        assertEquals(List.of("MEETS_DEVICE_INTEGRITY"), verdict.getDeviceIntegrity().getDeviceRecognitionVerdict());
        assertEquals("LICENSED", verdict.getAccountDetails().getAppLicensingVerdict());
    }

    @Test
    public void deserialize_whenUnevaluatedVerdict_expectMissingObjectsNull() throws Exception {
        // Given: Google omits appIntegrity details and deviceIntegrity content when UNEVALUATED
        String json = "{\"tokenPayloadExternal\":{\"appIntegrity\":{\"appRecognitionVerdict\":\"UNEVALUATED\"},"
                + "\"deviceIntegrity\":{}}}";

        // When
        PlayIntegrityDecodeResponse response = mapper.readValue(json, PlayIntegrityDecodeResponse.class);

        // Then: evaluation must be null-safe against every level
        PlayIntegrityDecodeResponse.Verdict verdict = response.getTokenPayloadExternal();
        assertNull(verdict.getRequestDetails());
        assertNull(verdict.getAppIntegrity().getPackageName());
        assertNull(verdict.getAppIntegrity().getCertificateSha256Digest());
        assertNull(verdict.getDeviceIntegrity().getDeviceRecognitionVerdict());
        assertNull(verdict.getAccountDetails());
    }
}
