package at.rtr.rmbt.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestSettingsRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void deserialize_whenIntegrityTokenPresent_expectRawTimestampString() throws Exception {
        // Given: integrity_timestamp is a JSON integer; server must keep the raw decimal text (spec §5.3)
        String json = "{\"uuid\":\"c373f294-f332-4f1a-999e-a87a12523f4b\","
                + "\"integrity_token\":\"opaque-token\",\"integrity_timestamp\":1719912345678}";

        // When
        TestSettingsRequest request = mapper.readValue(json, TestSettingsRequest.class);

        // Then
        assertEquals("opaque-token", request.getIntegrityToken());
        assertEquals("1719912345678", request.getIntegrityTimestamp());
        assertNull(request.getIntegrityError());
    }

    @Test
    public void deserialize_whenIntegrityError_expectErrorAndDetail() throws Exception {
        // Given
        String json = "{\"integrity_error\":\"TIMEOUT\",\"integrity_error_detail\":\"STANDARD_ERROR_-9\"}";

        // When
        TestSettingsRequest request = mapper.readValue(json, TestSettingsRequest.class);

        // Then: lenient String binding, unknown future values must not fail
        assertEquals("TIMEOUT", request.getIntegrityError());
        assertEquals("STANDARD_ERROR_-9", request.getIntegrityErrorDetail());
        assertNull(request.getIntegrityToken());
    }

    @Test
    public void deserialize_whenNoIntegrityFields_expectNulls() throws Exception {
        // Given: old client
        String json = "{\"uuid\":\"c373f294-f332-4f1a-999e-a87a12523f4b\"}";

        // When
        TestSettingsRequest request = mapper.readValue(json, TestSettingsRequest.class);

        // Then
        assertNull(request.getIntegrityToken());
        assertNull(request.getIntegrityTimestamp());
        assertNull(request.getIntegrityError());
        assertNull(request.getIntegrityErrorDetail());
    }
}
