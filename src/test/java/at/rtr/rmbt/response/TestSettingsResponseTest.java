package at.rtr.rmbt.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSettingsResponseTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    public void serialize_whenErrorFlagsSet_expectJsonArrayOfStrings() throws Exception {
        // Given: rejection response shape required by deployed clients (spec §5.4)
        TestSettingsResponse response = TestSettingsResponse.builder()
                .errorList(List.of("Integrity check failed"))
                .errorFlags(List.of("TEST_REJECTED"))
                .build();

        // When
        String json = mapper.writeValueAsString(response);

        // Then
        assertTrue(json.contains("\"error_flags\":[\"TEST_REJECTED\"]"));
        assertTrue(json.contains("\"error\":[\"Integrity check failed\"]"));
    }

    @Test
    public void serialize_whenErrorFlagsNull_expectFieldAbsent() throws Exception {
        // Given: normal success response
        TestSettingsResponse response = TestSettingsResponse.builder()
                .testUuid("8c8946bb-e251-42f8-b0d1-43f972c2e216")
                .build();

        // When
        String json = mapper.writeValueAsString(response);

        // Then: NON_NULL inclusion keeps the field out — no change for existing clients
        assertFalse(json.contains("error_flags"));
    }
}
