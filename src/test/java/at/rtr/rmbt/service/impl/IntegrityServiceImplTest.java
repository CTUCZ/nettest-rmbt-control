package at.rtr.rmbt.service.impl;

import at.rtr.rmbt.dto.IntegrityCheckOutcome;
import at.rtr.rmbt.dto.IntegrityDecodeResult;
import at.rtr.rmbt.dto.PlayIntegrityDecodeResponse;
import at.rtr.rmbt.enums.IntegrityAction;
import at.rtr.rmbt.enums.IntegrityStatus;
import at.rtr.rmbt.enums.TestPlatform;
import at.rtr.rmbt.model.RtrClient;
import at.rtr.rmbt.model.TestIntegrity;
import at.rtr.rmbt.properties.ApplicationProperties;
import at.rtr.rmbt.properties.IntegrityProperties;
import at.rtr.rmbt.repository.TestIntegrityRepository;
import at.rtr.rmbt.request.TestSettingsRequest;
import at.rtr.rmbt.response.TestSettingsResponse;
import at.rtr.rmbt.service.ClientService;
import at.rtr.rmbt.service.IntegrityVerdictClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static at.rtr.rmbt.constant.IntegrityConstants.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class IntegrityServiceImplTest {

    private static final UUID CLIENT_UUID = UUID.fromString("c373f294-f332-4f1a-999e-a87a12523f4b");

    private IntegrityProperties properties;
    private ApplicationProperties applicationProperties;
    private IntegrityVerdictClient verdictClient;
    private ObjectProvider<IntegrityVerdictClient> verdictClientProvider;
    private IntegrityVerdictEvaluator evaluator;
    private IntegrityPolicyService policy;
    private TestIntegrityRepository repository;
    private ClientService clientService;
    private MessageSource messageSource;

    private IntegrityServiceImpl service;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        properties = new IntegrityProperties();
        properties.setEnabled(true);
        applicationProperties = new ApplicationProperties(
                new ApplicationProperties.LanguageProperties(Set.of("en", "cs"), "en"),
                Set.of("RMBT"), "1.2", 3, 7, 10, 10000, 2000);
        verdictClient = mock(IntegrityVerdictClient.class);
        verdictClientProvider = mock(ObjectProvider.class);
        when(verdictClientProvider.getIfAvailable()).thenReturn(verdictClient);
        evaluator = mock(IntegrityVerdictEvaluator.class);
        policy = mock(IntegrityPolicyService.class);
        repository = mock(TestIntegrityRepository.class);
        clientService = mock(ClientService.class);
        messageSource = mock(MessageSource.class);
        when(repository.save(any(TestIntegrity.class))).thenAnswer(inv -> {
            TestIntegrity entity = inv.getArgument(0);
            entity.setUid(42L);
            return entity;
        });
        when(clientService.getClientByUUID(CLIENT_UUID)).thenReturn(new RtrClient());
        when(policy.decide(any(), any(), any())).thenReturn(IntegrityAction.ALLOWED);

        service = new IntegrityServiceImpl(properties, applicationProperties, verdictClientProvider,
                evaluator, policy, repository, clientService, messageSource);
    }

    private TestSettingsRequest requestWithToken() {
        return TestSettingsRequest.builder()
                .platform(TestPlatform.ANDROID)
                .uuid(CLIENT_UUID.toString())
                .softwareVersionCode(40100)
                .integrityToken("valid-token")
                .integrityTimestamp("1719912345678")
                .build();
    }

    @Test
    public void check_whenDisabled_expectNullAndNoInteraction() {
        // Given
        properties.setEnabled(false);

        // When / Then
        assertNull(service.check(requestWithToken()));
        verifyNoInteractions(repository, verdictClient);
    }

    @Test
    public void check_whenEnabledWithoutVerdictClient_expectNullFeatureOff() {
        // Given: enabled=true but no usable credentials -> no client bean -> feature off (design par. 7)
        when(verdictClientProvider.getIfAvailable()).thenReturn(null);

        // When / Then
        assertNull(service.check(requestWithToken()));
        verifyNoInteractions(repository);
    }

    @Test
    public void check_whenNoIntegrityFieldsAndMonitor_expectNullWithoutRow() {
        // Given: old app / iOS / web client, policy allows
        TestSettingsRequest request = TestSettingsRequest.builder()
                .platform(TestPlatform.IOS).uuid(CLIENT_UUID.toString()).build();

        // When
        IntegrityCheckOutcome outcome = service.check(request);

        // Then: allowed MISSING is not persisted (would flood the table)
        assertNull(outcome);
        verify(repository, never()).save(any());
    }

    @Test
    public void check_whenNoIntegrityFieldsAndRejected_expectPersistedRejectRow() {
        // Given: enforce + reject-missing-fields, Android old app
        when(policy.decide(eq(TestPlatform.ANDROID), eq(IntegrityStatus.MISSING), any()))
                .thenReturn(IntegrityAction.REJECTED);
        TestSettingsRequest request = TestSettingsRequest.builder()
                .platform(TestPlatform.ANDROID).uuid(CLIENT_UUID.toString()).build();

        // When
        IntegrityCheckOutcome outcome = service.check(request);

        // Then: rejected requests are always recorded so support can look them up
        assertEquals(IntegrityAction.REJECTED, outcome.getAction());
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertEquals(PROVIDER_NONE, captor.getValue().getProvider());
        assertEquals(IntegrityStatus.MISSING, captor.getValue().getStatus());
        assertEquals(IntegrityAction.REJECTED, captor.getValue().getAction());
        assertEquals(CLIENT_UUID, captor.getValue().getClientUuid());
    }

    @Test
    public void check_whenIntegrityErrorOnly_expectClientErrorRowWithTruncatedDetail() {
        // Given
        TestSettingsRequest request = TestSettingsRequest.builder()
                .platform(TestPlatform.ANDROID)
                .uuid(CLIENT_UUID.toString())
                .integrityError("SOME_FUTURE_VALUE_LONGER_THAN_COLUMN")
                .integrityErrorDetail("x".repeat(300))
                .build();

        // When
        IntegrityCheckOutcome outcome = service.check(request);

        // Then
        assertEquals(IntegrityAction.ALLOWED, outcome.getAction());
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertEquals(IntegrityStatus.CLIENT_ERROR, captor.getValue().getStatus());
        // lenient handling: unknown value truncated to column size, never an error
        assertEquals(20, captor.getValue().getClientError().length());
        assertEquals(200, captor.getValue().getClientErrorDetail().length());
        verifyNoInteractions(verdictClient);
    }

    @Test
    public void check_whenTokenValid_expectPassRowWithDigestAndLatency() {
        // Given
        PlayIntegrityDecodeResponse.Verdict verdict = new PlayIntegrityDecodeResponse.Verdict();
        when(verdictClient.decode("valid-token")).thenReturn(IntegrityDecodeResult.builder()
                .outcome(IntegrityDecodeResult.Outcome.OK).verdict(verdict).latencyMs(123).build());
        when(evaluator.evaluate(eq(verdict), eq(CLIENT_UUID.toString()), eq("1719912345678")))
                .thenReturn(List.of());

        // When
        IntegrityCheckOutcome outcome = service.check(requestWithToken());

        // Then
        assertEquals(Long.valueOf(42L), outcome.getRecordUid());
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        TestIntegrity row = captor.getValue();
        assertEquals(IntegrityStatus.PASS, row.getStatus());
        assertEquals(PROVIDER_PLAY_INTEGRITY, row.getProvider());
        assertEquals(64, row.getTokenDigest().length());
        assertEquals(Integer.valueOf(123), row.getDecodeLatencyMs());
        assertEquals(Integer.valueOf(40100), row.getSoftwareVersionCode());
        assertEquals("ANDROID", row.getPlatform());
    }

    @Test
    public void check_whenVerdictChecksFail_expectFailRow() {
        // Given
        PlayIntegrityDecodeResponse.Verdict verdict = new PlayIntegrityDecodeResponse.Verdict();
        when(verdictClient.decode(any())).thenReturn(IntegrityDecodeResult.builder()
                .outcome(IntegrityDecodeResult.Outcome.OK).verdict(verdict).latencyMs(50).build());
        when(evaluator.evaluate(any(), any(), any())).thenReturn(List.of(CHECK_DEVICE_INTEGRITY_FAILED));

        // When
        service.check(requestWithToken());

        // Then
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertEquals(IntegrityStatus.FAIL, captor.getValue().getStatus());
        assertEquals(List.of(CHECK_DEVICE_INTEGRITY_FAILED), captor.getValue().getFailedChecks());
    }

    @Test
    public void check_whenDecodeReturns4xx_expectFailRowWithDecodeFailed() {
        // Given: invalid token must be FAIL, not UNAVAILABLE (bypass protection)
        when(verdictClient.decode(any())).thenReturn(IntegrityDecodeResult.builder()
                .outcome(IntegrityDecodeResult.Outcome.INVALID_TOKEN).latencyMs(30).build());

        // When
        service.check(requestWithToken());

        // Then
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertEquals(IntegrityStatus.FAIL, captor.getValue().getStatus());
        assertEquals(List.of(CHECK_DECODE_FAILED), captor.getValue().getFailedChecks());
    }

    @Test
    public void check_whenQuotaExceeded_expectUnavailableRow() {
        // Given
        when(verdictClient.decode(any())).thenReturn(IntegrityDecodeResult.builder()
                .outcome(IntegrityDecodeResult.Outcome.QUOTA_EXCEEDED).latencyMs(30).build());

        // When
        service.check(requestWithToken());

        // Then: distinguished from generic unavailability for quota-bypass alerting
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertEquals(IntegrityStatus.UNAVAILABLE, captor.getValue().getStatus());
        assertEquals(List.of(CHECK_QUOTA_EXCEEDED), captor.getValue().getFailedChecks());
    }

    @Test
    public void check_whenTokenBlank_expectFailWithoutGoogleCall() {
        // Given: empty token = negative verdict without calling Google (spec par. 6.6)
        TestSettingsRequest request = requestWithToken();
        request.setIntegrityToken("");

        // When
        service.check(request);

        // Then
        verifyNoInteractions(verdictClient);
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertEquals(IntegrityStatus.FAIL, captor.getValue().getStatus());
        assertEquals(List.of(CHECK_DECODE_FAILED), captor.getValue().getFailedChecks());
    }

    @Test
    public void check_whenTokenTooLarge_expectFailWithoutGoogleCall() {
        // Given
        TestSettingsRequest request = requestWithToken();
        request.setIntegrityToken("x".repeat(25_000));

        // When
        service.check(request);

        // Then: quota/DoS protection - no decode call
        verifyNoInteractions(verdictClient);
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertEquals(IntegrityStatus.FAIL, captor.getValue().getStatus());
        assertEquals(List.of(CHECK_TOKEN_TOO_LARGE), captor.getValue().getFailedChecks());
    }

    @Test
    public void check_whenTokenWithoutUuid_expectFailUuidMissing() {
        // Given: inconsistent client (spec par. 5.3)
        TestSettingsRequest request = requestWithToken();
        request.setUuid(null);

        // When
        service.check(request);

        // Then
        verifyNoInteractions(verdictClient);
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().getFailedChecks().contains(CHECK_UUID_MISSING));
    }

    @Test
    public void check_whenTokenWithoutTimestamp_expectFailTimestampMissing() {
        // Given
        TestSettingsRequest request = requestWithToken();
        request.setIntegrityTimestamp(null);

        // When
        service.check(request);

        // Then
        verifyNoInteractions(verdictClient);
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().getFailedChecks().contains(CHECK_TIMESTAMP_MISSING));
    }

    @Test
    public void check_whenClientUnknown_expectFailWithoutGoogleCall() {
        // Given: don't spend quota on requests the facade will refuse anyway
        when(clientService.getClientByUUID(CLIENT_UUID)).thenReturn(null);

        // When
        service.check(requestWithToken());

        // Then
        verifyNoInteractions(verdictClient);
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertEquals(List.of(CHECK_CLIENT_UNKNOWN), captor.getValue().getFailedChecks());
    }

    @Test
    public void check_whenGoogleUnavailable_expectUnavailableRow() {
        // Given: Google outage (5xx/timeout/network) must fail open, distinguished from quota
        when(verdictClient.decode(any())).thenReturn(IntegrityDecodeResult.builder()
                .outcome(IntegrityDecodeResult.Outcome.UNAVAILABLE).latencyMs(30).build());

        // When
        service.check(requestWithToken());

        // Then
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertEquals(IntegrityStatus.UNAVAILABLE, captor.getValue().getStatus());
        assertEquals(List.of(CHECK_GOOGLE_UNAVAILABLE), captor.getValue().getFailedChecks());
    }

    @Test
    public void check_whenOtherIntegrityViolation_expectExceptionPropagated() {
        // Given: a non-replay constraint violation must surface, not be misclassified as replay
        PlayIntegrityDecodeResponse.Verdict verdict = new PlayIntegrityDecodeResponse.Verdict();
        when(verdictClient.decode(any())).thenReturn(IntegrityDecodeResult.builder()
                .outcome(IntegrityDecodeResult.Outcome.OK).verdict(verdict).latencyMs(20).build());
        when(evaluator.evaluate(any(), any(), any())).thenReturn(List.of());
        when(repository.save(any(TestIntegrity.class)))
                .thenThrow(new DataIntegrityViolationException("null value in column \"provider\" violates not-null constraint"));

        // When / Then
        try {
            service.check(requestWithToken());
            fail("expected DataIntegrityViolationException");
        } catch (DataIntegrityViolationException expected) {
            verify(repository, times(1)).save(any(TestIntegrity.class));
        }
    }

    @Test
    public void check_whenTokenDigestAlreadySeen_expectReplayRowWithoutDigestAndRedecidedAction() {
        // Given: unique index violation = replayed token (first-seen-wins across instances);
        // enforce mode rejects FAIL, so the replay must flip the returned action too
        PlayIntegrityDecodeResponse.Verdict verdict = new PlayIntegrityDecodeResponse.Verdict();
        when(verdictClient.decode(any())).thenReturn(IntegrityDecodeResult.builder()
                .outcome(IntegrityDecodeResult.Outcome.OK).verdict(verdict).latencyMs(20).build());
        when(evaluator.evaluate(any(), any(), any())).thenReturn(List.of());
        when(policy.decide(any(), eq(IntegrityStatus.FAIL), any())).thenReturn(IntegrityAction.REJECTED);
        when(repository.save(any(TestIntegrity.class)))
                .thenThrow(new DataIntegrityViolationException("test_integrity_token_digest_uq"))
                .thenAnswer(inv -> {
                    TestIntegrity entity = inv.getArgument(0);
                    entity.setUid(43L);
                    return entity;
                });

        // When
        IntegrityCheckOutcome outcome = service.check(requestWithToken());

        // Then: the outcome reflects the PERSISTED replay row, not the pre-insert state
        assertEquals(IntegrityAction.REJECTED, outcome.getAction());
        assertEquals(Long.valueOf(43L), outcome.getRecordUid());

        // Then: second save is a FRESH entity (the first instance is unusable after a failed flush)
        // with REPLAY + FAIL, no digest column (unique index) and the digest kept in failed_checks
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository, times(2)).save(captor.capture());
        TestIntegrity replayRow = captor.getAllValues().get(1);
        assertNotSame(captor.getAllValues().get(0), replayRow);
        assertEquals(IntegrityStatus.FAIL, replayRow.getStatus());
        assertTrue(replayRow.getFailedChecks().contains(CHECK_REPLAY));
        assertTrue(replayRow.getFailedChecks().stream().anyMatch(c -> c.startsWith(CHECK_REPLAY_DIGEST_PREFIX)));
        assertNull(replayRow.getTokenDigest());
    }

    @Test
    public void check_whenBothTokenAndErrorPresent_expectTokenEvaluated() {
        // Given: agreed edge case (spec par. 6.6) - token wins, inconsistency only logged
        PlayIntegrityDecodeResponse.Verdict verdict = new PlayIntegrityDecodeResponse.Verdict();
        when(verdictClient.decode(any())).thenReturn(IntegrityDecodeResult.builder()
                .outcome(IntegrityDecodeResult.Outcome.OK).verdict(verdict).latencyMs(20).build());
        when(evaluator.evaluate(any(), any(), any())).thenReturn(List.of());
        TestSettingsRequest request = requestWithToken();
        request.setIntegrityError("TIMEOUT");

        // When
        service.check(request);

        // Then
        verify(verdictClient).decode("valid-token");
        ArgumentCaptor<TestIntegrity> captor = ArgumentCaptor.forClass(TestIntegrity.class);
        verify(repository).save(captor.capture());
        assertEquals(IntegrityStatus.PASS, captor.getValue().getStatus());
    }

    @Test
    public void attachTest_expectDelegatesToRepository() {
        // Given
        UUID testUuid = UUID.randomUUID();

        // When
        service.attachTest(42L, testUuid);

        // Then
        verify(repository).attachTest(42L, testUuid);
    }

    @Test
    public void buildRejectionResponse_expectLocalizedErrorAndTestRejectedFlag() {
        // Given
        when(messageSource.getMessage(eq(MESSAGE_KEY_INTEGRITY_CHECK), any(), any(Locale.class)))
                .thenReturn("localized message");

        // When
        TestSettingsResponse response = service.buildRejectionResponse("cs");

        // Then
        assertEquals(List.of("localized message"), response.getErrorList());
        assertEquals(List.of(ERROR_FLAG_TEST_REJECTED), response.getErrorFlags());
        assertNull(response.getTestToken());
    }
}
