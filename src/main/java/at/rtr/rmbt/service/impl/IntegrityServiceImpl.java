package at.rtr.rmbt.service.impl;

import at.rtr.rmbt.dto.IntegrityCheckOutcome;
import at.rtr.rmbt.dto.IntegrityDecodeResult;
import at.rtr.rmbt.enums.IntegrityAction;
import at.rtr.rmbt.enums.IntegrityStatus;
import at.rtr.rmbt.enums.TestPlatform;
import at.rtr.rmbt.model.TestIntegrity;
import at.rtr.rmbt.properties.ApplicationProperties;
import at.rtr.rmbt.properties.IntegrityProperties;
import at.rtr.rmbt.repository.TestIntegrityRepository;
import at.rtr.rmbt.request.TestSettingsRequest;
import at.rtr.rmbt.response.TestSettingsResponse;
import at.rtr.rmbt.service.ClientService;
import at.rtr.rmbt.service.IntegrityService;
import at.rtr.rmbt.service.IntegrityVerdictClient;
import at.rtr.rmbt.utils.HashUtils;
import at.rtr.rmbt.utils.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static at.rtr.rmbt.constant.IntegrityConstants.*;

/**
 * Orchestrates the Play Integrity check for /testRequest (design par. 4): cheap pre-checks
 * (no Google quota spent on garbage), decode, verdict evaluation, policy decision and persistence.
 * Deliberately NOT @Transactional: the Google call must never hold a DB connection; each
 * repository.save() runs its own short transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrityServiceImpl implements IntegrityService {

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d{1,19}");
    private static final int CLIENT_ERROR_MAX = 20;
    private static final int CLIENT_ERROR_DETAIL_MAX = 200;
    private static final String TOKEN_DIGEST_UNIQUE_INDEX = "test_integrity_token_digest_uq";

    private final IntegrityProperties properties;
    private final ApplicationProperties applicationProperties;
    private final ObjectProvider<IntegrityVerdictClient> verdictClientProvider;
    private final IntegrityVerdictEvaluator evaluator;
    private final IntegrityPolicyService policy;
    private final TestIntegrityRepository repository;
    private final ClientService clientService;
    private final MessageSource messageSource;

    @Override
    public IntegrityCheckOutcome check(TestSettingsRequest request) {
        // Feature is OFF when disabled OR when no verdict client bean exists (enabled without
        // usable credentials - see IntegrityConfig): no evaluation, no rows (design par. 7).
        if (!properties.isEnabled() || verdictClientProvider.getIfAvailable() == null) {
            return null;
        }
        // The integrity feature is fail-open by design (a Google outage must not block
        // measurements), and that guarantee must also cover the feature's OWN infrastructure
        // failures (e.g. the test_integrity table missing before ops applies the migration) -
        // never let those surface as an HTTP 500 for /testRequest. A rejection is a normal return
        // value (IntegrityAction.REJECTED), never an exception, so no rejection can be swallowed
        // by this catch.
        try {
            TestPlatform platform = request.getPlatform();
            String token = request.getIntegrityToken();
            String integrityError = request.getIntegrityError();

            if (token == null && integrityError == null) {
                return checkMissingFields(request, platform);
            }

            TestIntegrity row = baseRow(request, platform);
            if (token != null) {
                if (integrityError != null) {
                    log.info("Inconsistent client: both integrity_token and integrity_error present, evaluating the token");
                }
                evaluateToken(request, token, row);
            } else {
                row.setProvider(PROVIDER_PLAY_INTEGRITY);
                row.setStatus(IntegrityStatus.CLIENT_ERROR);
            }
            // Policy decides on the RAW client-supplied value, not the column-truncated one stored on
            // the row: a configured reject value longer than CLIENT_ERROR_MAX must still match.
            row.setAction(policy.decide(platform, row.getStatus(), integrityError));

            logResult(row);
            // The outcome MUST be built from the persisted entity: the replay fallback re-decides
            // the action (FAIL can mean REJECTED in enforce mode) - returning the pre-insert action
            // would let a replayed token through while the DB says REJECTED.
            TestIntegrity saved = persistWithReplayFallback(row, platform, integrityError);
            return IntegrityCheckOutcome.builder().recordUid(saved.getUid()).action(saved.getAction()).build();
        } catch (RuntimeException e) {
            log.error(ALERT_CHECK_FAILED + " - integrity check failed, allowing the request (fail-open)", e);
            return null;
        }
    }

    /** No integrity fields: only persisted when the policy actually rejects (support lookup). */
    private IntegrityCheckOutcome checkMissingFields(TestSettingsRequest request, TestPlatform platform) {
        IntegrityAction action = policy.decide(platform, IntegrityStatus.MISSING, null);
        if (action == IntegrityAction.ALLOWED) {
            return null;
        }
        TestIntegrity row = baseRow(request, platform);
        row.setProvider(PROVIDER_NONE);
        row.setStatus(IntegrityStatus.MISSING);
        row.setAction(action);
        logResult(row);
        TestIntegrity saved = repository.save(row);
        return IntegrityCheckOutcome.builder().recordUid(saved.getUid()).action(saved.getAction()).build();
    }

    /**
     * One INFO line per evaluation, logged BEFORE the insert so the result is visible even when
     * the save fails (e.g. missing DB grants) - the log-side counterpart of the test_integrity
     * row. Requests without integrity fields that the policy allows (every iOS/web/legacy request)
     * are deliberately NOT logged: they persist no row and would drown the log. A replay re-decision
     * is logged separately by persistWithReplayFallback. Never logs the token itself, only its digest.
     */
    private void logResult(TestIntegrity row) {
        log.info("Integrity check result: client={}, platform={}, provider={}, status={}, action={}, "
                        + "failedChecks={}, clientError={}, decodeLatencyMs={}, tokenDigest={}",
                row.getClientUuid(), row.getPlatform(), row.getProvider(), row.getStatus(),
                row.getAction(), row.getFailedChecks(), row.getClientError(),
                row.getDecodeLatencyMs(), row.getTokenDigest());
    }

    private TestIntegrity baseRow(TestSettingsRequest request, TestPlatform platform) {
        return TestIntegrity.builder()
                .clientUuid(parseClientUuid(request.getUuid()))
                .platform(platform == null ? null : platform.name())
                .softwareVersionCode(request.getSoftwareVersionCode())
                .clientError(StringUtils.truncate(request.getIntegrityError(), CLIENT_ERROR_MAX))
                .clientErrorDetail(StringUtils.truncate(request.getIntegrityErrorDetail(), CLIENT_ERROR_DETAIL_MAX))
                .failedChecks(new ArrayList<>())
                .build();
    }

    private void evaluateToken(TestSettingsRequest request, String token, TestIntegrity row) {
        row.setProvider(PROVIDER_PLAY_INTEGRITY);
        List<String> failed = row.getFailedChecks();

        // Cheap pre-checks: never spend a Google decode call on requests that cannot pass
        if (StringUtils.isBlank(token)) {
            failed.add(CHECK_DECODE_FAILED); // empty token = negative verdict without calling Google (spec par. 6.6)
        } else if (token.getBytes(StandardCharsets.UTF_8).length > properties.getMaxTokenBytes()) {
            failed.add(CHECK_TOKEN_TOO_LARGE);
        }
        String rawUuid = request.getUuid();
        if (StringUtils.isBlank(rawUuid)) {
            failed.add(CHECK_UUID_MISSING);
        }
        String rawTimestamp = request.getIntegrityTimestamp();
        if (rawTimestamp == null || !TIMESTAMP_PATTERN.matcher(rawTimestamp).matches()) {
            failed.add(CHECK_TIMESTAMP_MISSING);
        }
        if (failed.isEmpty()) {
            // row.getClientUuid() was already parsed in baseRow(); reuse it instead of re-parsing
            // rawUuid. It is null in exactly the same cases parseClientUuid(rawUuid) would be.
            UUID clientUuid = row.getClientUuid();
            // Unparseable uuid short-circuits without a DB query
            if (clientUuid == null || clientService.getClientByUUID(clientUuid) == null) {
                failed.add(CHECK_CLIENT_UNKNOWN);
            }
        }
        if (!failed.isEmpty()) {
            row.setStatus(IntegrityStatus.FAIL);
            return;
        }

        // Non-null guaranteed by the early return in check()
        IntegrityVerdictClient client = verdictClientProvider.getIfAvailable();
        IntegrityDecodeResult decode = client.decode(token);
        row.setDecodeLatencyMs((int) decode.getLatencyMs());
        row.setTokenDigest(HashUtils.sha256Hex(token));
        switch (decode.getOutcome()) {
            case OK -> {
                failed.addAll(evaluator.evaluate(decode.getVerdict(), rawUuid, rawTimestamp));
                row.setStatus(failed.isEmpty() ? IntegrityStatus.PASS : IntegrityStatus.FAIL);
            }
            case INVALID_TOKEN -> {
                failed.add(CHECK_DECODE_FAILED);
                row.setStatus(IntegrityStatus.FAIL);
            }
            case QUOTA_EXCEEDED -> {
                failed.add(CHECK_QUOTA_EXCEEDED);
                row.setStatus(IntegrityStatus.UNAVAILABLE);
                log.warn(ALERT_QUOTA_EXCEEDED);
            }
            case UNAVAILABLE -> {
                failed.add(CHECK_GOOGLE_UNAVAILABLE);
                row.setStatus(IntegrityStatus.UNAVAILABLE);
                log.warn(ALERT_GOOGLE_UNAVAILABLE);
            }
        }
    }

    /**
     * Insert-or-fail anti-replay: the partial unique index on token_digest makes the first insert
     * win atomically across instances; a violation means the token was already used. The fallback
     * MUST persist a fresh entity instance - after a failed flush the Hibernate session state of
     * the original instance is undefined (and open-in-view shares one session per request).
     * The replayed digest goes into failed_checks (tokenDigest must stay NULL so the unique index
     * does not block the insert) so support can find WHICH token was replayed.
     */
    private TestIntegrity persistWithReplayFallback(TestIntegrity row, TestPlatform platform, String rawClientError) {
        try {
            return repository.save(row);
        } catch (DataIntegrityViolationException e) {
            if (!isReplayConstraintViolation(e)) {
                // Not the anti-replay unique index: a different constraint violation is a
                // schema/programming bug that must surface, not be misclassified as a replay.
                log.error("Non-replay integrity constraint violation on save", e);
                throw e;
            }
            log.info("Replayed integrity token detected (digest {})", row.getTokenDigest());
            List<String> failed = new ArrayList<>(row.getFailedChecks());
            failed.add(CHECK_REPLAY);
            failed.add(CHECK_REPLAY_DIGEST_PREFIX + row.getTokenDigest());
            TestIntegrity replayRow = TestIntegrity.builder()
                    .testUid(row.getTestUid())
                    .clientUuid(row.getClientUuid())
                    .platform(row.getPlatform())
                    .provider(row.getProvider())
                    .status(IntegrityStatus.FAIL)
                    .action(policy.decide(platform, IntegrityStatus.FAIL, rawClientError))
                    .failedChecks(failed)
                    .clientError(row.getClientError())
                    .clientErrorDetail(row.getClientErrorDetail())
                    .softwareVersionCode(row.getSoftwareVersionCode())
                    .tokenDigest(null)
                    .decodeLatencyMs(row.getDecodeLatencyMs())
                    .build();
            return repository.save(replayRow);
        }
    }

    /**
     * Prefer the structured constraint name (set by the JDBC driver via the SQLState -> Hibernate's
     * {@link ConstraintViolationException}) over sniffing the exception message: the message format
     * is driver/locale-dependent and not a contract, while the constraint name is stable. Falls back
     * to the message-contains check when no such cause is present (e.g. a different driver/version
     * that does not populate it).
     */
    private boolean isReplayConstraintViolation(DataIntegrityViolationException e) {
        ConstraintViolationException constraintViolation = findConstraintViolationCause(e);
        String constraintName = constraintViolation == null ? null : constraintViolation.getConstraintName();
        if (constraintName != null) {
            return TOKEN_DIGEST_UNIQUE_INDEX.equalsIgnoreCase(constraintName);
        }
        String rootMessage = e.getMostSpecificCause().getMessage();
        return rootMessage != null && rootMessage.contains(TOKEN_DIGEST_UNIQUE_INDEX);
    }

    private ConstraintViolationException findConstraintViolationCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException) {
                return constraintViolationException;
            }
            cause = cause.getCause();
        }
        return null;
    }

    @Override
    public void attachTest(Long recordUid, UUID testUuid) {
        try {
            int updated = repository.attachTest(recordUid, testUuid);
            if (updated == 0) {
                log.warn("attachTest updated no rows (recordUid={}, testUuid={}) - integrity record may be missing",
                        recordUid, testUuid);
            }
        } catch (RuntimeException e) {
            // The facade already committed and returned test_token to the client at this point -
            // a failing UPDATE here must never break the response (fail-open, same as check()).
            log.error(ALERT_CHECK_FAILED + " - attachTest failed (recordUid={}, testUuid={})",
                    recordUid, testUuid, e);
        }
    }

    @Override
    public TestSettingsResponse buildRejectionResponse(String language) {
        Locale locale = MessageUtils.getLocaleFormLanguage(language, applicationProperties.getLanguage());
        String message = messageSource.getMessage(MESSAGE_KEY_INTEGRITY_CHECK, null, locale);
        return TestSettingsResponse.builder()
                .errorList(List.of(message))
                .errorFlags(List.of(ERROR_FLAG_TEST_REJECTED))
                .build();
    }

    /** Accepts the optional legacy "U" prefix; returns null for unparseable input (lenient). */
    private UUID parseClientUuid(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String value = raw.startsWith("U") && raw.length() > 1 ? raw.substring(1) : raw;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
