package at.rtr.rmbt.service.impl;

import at.rtr.rmbt.dto.PlayIntegrityDecodeResponse;
import at.rtr.rmbt.properties.IntegrityProperties;
import at.rtr.rmbt.utils.HashUtils;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static at.rtr.rmbt.constant.IntegrityConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntegrityVerdictEvaluatorTest {

    private static final String UUID_RAW = "c373f294-f332-4f1a-999e-a87a12523f4b";
    private static final String TS_RAW = "1719912345678";
    private static final long NOW_MS = 1_719_912_400_000L; // ~55 s after the Google timestamp below

    private IntegrityProperties properties;
    private IntegrityVerdictEvaluator evaluator;

    @Before
    public void setUp() {
        properties = new IntegrityProperties();
        properties.setCertificateDigests(List.of("expected-digest"));
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(NOW_MS), ZoneOffset.UTC);
        evaluator = new IntegrityVerdictEvaluator(properties, fixedClock);
    }

    /** Verdict where every check passes; individual tests break exactly one thing. */
    private PlayIntegrityDecodeResponse.Verdict passingVerdict() {
        PlayIntegrityDecodeResponse.Verdict verdict = new PlayIntegrityDecodeResponse.Verdict();

        PlayIntegrityDecodeResponse.RequestDetails rd = new PlayIntegrityDecodeResponse.RequestDetails();
        rd.setRequestPackageName("cz.ctu.rmbt.android.prod");
        rd.setRequestHash(HashUtils.sha256Hex(UUID_RAW + "|" + TS_RAW));
        rd.setTimestampMillis(String.valueOf(NOW_MS - 55_000));
        verdict.setRequestDetails(rd);

        PlayIntegrityDecodeResponse.AppIntegrity ai = new PlayIntegrityDecodeResponse.AppIntegrity();
        ai.setAppRecognitionVerdict("PLAY_RECOGNIZED");
        ai.setPackageName("cz.ctu.rmbt.android.prod");
        ai.setCertificateSha256Digest(List.of("expected-digest"));
        verdict.setAppIntegrity(ai);

        PlayIntegrityDecodeResponse.DeviceIntegrity di = new PlayIntegrityDecodeResponse.DeviceIntegrity();
        di.setDeviceRecognitionVerdict(List.of("MEETS_DEVICE_INTEGRITY", "MEETS_BASIC_INTEGRITY"));
        verdict.setDeviceIntegrity(di);

        return verdict;
    }

    @Test
    public void evaluate_whenEverythingValid_expectNoFailedChecks() {
        // When
        List<String> failed = evaluator.evaluate(passingVerdict(), UUID_RAW, TS_RAW);

        // Then
        assertTrue(failed.isEmpty());
    }

    @Test
    public void evaluate_whenRequestHashDiffers_expectHashMismatch() {
        // Given: token was minted for a different uuid/timestamp (replay / harvesting)
        PlayIntegrityDecodeResponse.Verdict verdict = passingVerdict();
        verdict.getRequestDetails().setRequestHash("0000000000000000000000000000000000000000000000000000000000000000");

        // When / Then
        assertEquals(List.of(CHECK_REQUEST_HASH_MISMATCH), evaluator.evaluate(verdict, UUID_RAW, TS_RAW));
    }

    @Test
    public void evaluate_whenGoogleTimestampOutsideWindow_expectStaleToken() {
        // Given: token minted 10 minutes ago (window is 5 minutes)
        PlayIntegrityDecodeResponse.Verdict verdict = passingVerdict();
        verdict.getRequestDetails().setTimestampMillis(String.valueOf(NOW_MS - 600_000));

        // When / Then
        assertEquals(List.of(CHECK_STALE_TOKEN), evaluator.evaluate(verdict, UUID_RAW, TS_RAW));
    }

    @Test
    public void evaluate_whenPackageNameDiffers_expectPackageMismatch() {
        // Given
        PlayIntegrityDecodeResponse.Verdict verdict = passingVerdict();
        verdict.getAppIntegrity().setPackageName("com.example.fake");

        // When / Then
        assertEquals(List.of(CHECK_PACKAGE_NAME_MISMATCH), evaluator.evaluate(verdict, UUID_RAW, TS_RAW));
    }

    @Test
    public void evaluate_whenAppNotRecognized_expectAppCheckFailed() {
        // Given: sideloaded/modified build -> UNRECOGNIZED_VERSION. Per Google's docs, package/
        // digest fields are only omitted when appRecognitionVerdict is UNEVALUATED, not for
        // UNRECOGNIZED_VERSION - but evaluate() must stay null-safe regardless, so this test nulls
        // them out explicitly to pin that defensive behavior.
        PlayIntegrityDecodeResponse.Verdict verdict = passingVerdict();
        verdict.getAppIntegrity().setAppRecognitionVerdict("UNRECOGNIZED_VERSION");
        verdict.getAppIntegrity().setPackageName(null);
        verdict.getAppIntegrity().setCertificateSha256Digest(null);

        // When
        List<String> failed = evaluator.evaluate(verdict, UUID_RAW, TS_RAW);

        // Then: null-safe, reports all three affected checks
        assertTrue(failed.contains(CHECK_APP_NOT_RECOGNIZED));
        assertTrue(failed.contains(CHECK_PACKAGE_NAME_MISMATCH));
        assertTrue(failed.contains(CHECK_CERT_DIGEST_MISMATCH));
    }

    @Test
    public void evaluate_whenDeviceIntegrityMissing_expectDeviceCheckFailed() {
        // Given: emulator/rooted device -> empty verdict list (or missing object)
        PlayIntegrityDecodeResponse.Verdict verdict = passingVerdict();
        verdict.getDeviceIntegrity().setDeviceRecognitionVerdict(List.of());

        // When / Then
        assertEquals(List.of(CHECK_DEVICE_INTEGRITY_FAILED), evaluator.evaluate(verdict, UUID_RAW, TS_RAW));
    }

    @Test
    public void evaluate_whenVerdictObjectsMissing_expectAllChecksFailedWithoutNpe() {
        // Given: fully UNEVALUATED verdict (e.g. Google replay protection emptied it)
        PlayIntegrityDecodeResponse.Verdict verdict = new PlayIntegrityDecodeResponse.Verdict();

        // When
        List<String> failed = evaluator.evaluate(verdict, UUID_RAW, TS_RAW);

        // Then
        assertTrue(failed.contains(CHECK_REQUEST_HASH_MISMATCH));
        assertTrue(failed.contains(CHECK_STALE_TOKEN));
        assertTrue(failed.contains(CHECK_PACKAGE_NAME_MISMATCH));
        assertTrue(failed.contains(CHECK_APP_NOT_RECOGNIZED));
        assertTrue(failed.contains(CHECK_CERT_DIGEST_MISMATCH));
        assertTrue(failed.contains(CHECK_DEVICE_INTEGRITY_FAILED));
    }

    @Test
    public void evaluate_whenNoDigestConfigured_expectCertCheckSkipped() {
        // Given: bootstrap phase before the app team delivers the signing digest
        properties.setCertificateDigests(List.of());
        PlayIntegrityDecodeResponse.Verdict verdict = passingVerdict();
        verdict.getAppIntegrity().setCertificateSha256Digest(List.of("whatever"));

        // When / Then: digest check does not fire
        assertTrue(evaluator.evaluate(verdict, UUID_RAW, TS_RAW).isEmpty());
    }
}
