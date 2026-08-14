package at.rtr.rmbt.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HashUtilsTest {

    @Test
    public void sha256Hex_whenKnownVector_expectKnownDigest() {
        // Given: NIST known-answer vector for "abc"
        String input = "abc";

        // When
        String actual = HashUtils.sha256Hex(input);

        // Then
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", actual);
    }

    @Test
    public void sha256Hex_whenRequestHashInput_expectLowercaseHexOfUuidPipeTimestamp() {
        // Given: request-hash canonical input <uuid> + "|" + <integrity_timestamp> (spec §5.3)
        String input = "c373f294-f332-4f1a-999e-a87a12523f4b|1719912345678";

        // When
        String actual = HashUtils.sha256Hex(input);

        // Then: 64 lowercase hex chars, deterministic
        assertEquals(64, actual.length());
        assertEquals(actual, actual.toLowerCase());
        assertEquals(HashUtils.sha256Hex("c373f294-f332-4f1a-999e-a87a12523f4b|1719912345678"), actual);
    }
}
