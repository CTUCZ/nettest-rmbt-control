package at.rtr.rmbt.service.impl;

import at.rtr.rmbt.dto.PlayIntegrityDecodeResponse;
import at.rtr.rmbt.properties.IntegrityProperties;
import at.rtr.rmbt.utils.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static at.rtr.rmbt.constant.IntegrityConstants.*;

/**
 * Evaluates a decoded Play Integrity verdict against the configured policy checks (design par. 4).
 * Null-safe on every level: Google omits nested objects for UNEVALUATED verdicts, and a missing
 * value always counts as a failed check, never an exception. The client-supplied raw uuid and
 * timestamp are used exactly as received on the wire (spec par. 5.3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrityVerdictEvaluator {

    private static final String PLAY_RECOGNIZED = "PLAY_RECOGNIZED";
    private static final String MEETS_DEVICE_INTEGRITY = "MEETS_DEVICE_INTEGRITY";

    private final IntegrityProperties properties;
    private final Clock clock;

    public List<String> evaluate(PlayIntegrityDecodeResponse.Verdict verdict, String rawUuid, String rawTimestamp) {
        List<String> failed = new ArrayList<>();

        PlayIntegrityDecodeResponse.RequestDetails requestDetails = verdict.getRequestDetails();
        PlayIntegrityDecodeResponse.AppIntegrity appIntegrity = verdict.getAppIntegrity();
        PlayIntegrityDecodeResponse.DeviceIntegrity deviceIntegrity = verdict.getDeviceIntegrity();

        String expectedHash = HashUtils.sha256Hex(rawUuid + "|" + rawTimestamp);
        if (requestDetails == null || !expectedHash.equals(requestDetails.getRequestHash())) {
            failed.add(CHECK_REQUEST_HASH_MISMATCH);
        }

        if (requestDetails == null || !isFresh(requestDetails.getTimestampMillis())) {
            failed.add(CHECK_STALE_TOKEN);
        }

        String expectedPackage = properties.getPackageName();
        boolean packageOk = requestDetails != null && appIntegrity != null
                && expectedPackage.equals(requestDetails.getRequestPackageName())
                && expectedPackage.equals(appIntegrity.getPackageName());
        if (!packageOk) {
            failed.add(CHECK_PACKAGE_NAME_MISMATCH);
        }

        if (appIntegrity == null || !PLAY_RECOGNIZED.equals(appIntegrity.getAppRecognitionVerdict())) {
            failed.add(CHECK_APP_NOT_RECOGNIZED);
        }

        List<String> configuredDigests = properties.getCertificateDigests();
        if (configuredDigests.isEmpty()) {
            log.warn("Certificate digest check skipped: app.integrity.certificate-digests is empty");
        } else {
            List<String> digests = appIntegrity == null ? null : appIntegrity.getCertificateSha256Digest();
            boolean digestOk = digests != null && digests.stream()
                    .anyMatch(configuredDigests::contains);
            if (!digestOk) {
                failed.add(CHECK_CERT_DIGEST_MISMATCH);
            }
        }

        List<String> deviceVerdicts = deviceIntegrity == null ? null : deviceIntegrity.getDeviceRecognitionVerdict();
        if (deviceVerdicts == null || !deviceVerdicts.contains(MEETS_DEVICE_INTEGRITY)) {
            failed.add(CHECK_DEVICE_INTEGRITY_FAILED);
        }

        PlayIntegrityDecodeResponse.AccountDetails accountDetails = verdict.getAccountDetails();
        String licensingVerdict = accountDetails == null ? null : accountDetails.getAppLicensingVerdict();
        if (!"LICENSED".equals(licensingVerdict)) {
            // Deliberately NOT enforced (fails for legitimate sideloads of the official APK);
            // logged only, as Phase 1 input for a possible later policy decision.
            log.info("appLicensingVerdict not LICENSED: {}", licensingVerdict);
        }

        return failed;
    }

    /** Freshness uses Google's own signed timestamp only - never the client-supplied integrity_timestamp. */
    private boolean isFresh(String timestampMillis) {
        if (timestampMillis == null) {
            return false;
        }
        try {
            long googleTs = Long.parseLong(timestampMillis);
            return Math.abs(clock.millis() - googleTs) <= properties.getFreshnessWindowMs();
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
