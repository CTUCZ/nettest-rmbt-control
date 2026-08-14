package at.rtr.rmbt.service;

import at.rtr.rmbt.dto.IntegrityCheckOutcome;
import at.rtr.rmbt.request.TestSettingsRequest;
import at.rtr.rmbt.response.TestSettingsResponse;

import java.util.UUID;

public interface IntegrityService {

    /**
     * Evaluates the Play Integrity contract for one /testRequest. Runs OUTSIDE any transaction
     * (contains a remote Google call). Returns null when the feature is disabled or when the
     * request carries no integrity fields and nothing is enforced.
     */
    IntegrityCheckOutcome check(TestSettingsRequest request);

    /** Links the persisted integrity record to the test created by the facade. */
    void attachTest(Long recordUid, UUID testUuid);

    /** Builds the rejection response: localized error + error_flags ["TEST_REJECTED"]. */
    TestSettingsResponse buildRejectionResponse(String language);
}
