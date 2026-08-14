package at.rtr.rmbt.controller;

import at.rtr.rmbt.constant.URIConstants;
import at.rtr.rmbt.dto.IntegrityCheckOutcome;
import at.rtr.rmbt.enums.IntegrityAction;
import at.rtr.rmbt.facade.TestSettingsFacade;
import at.rtr.rmbt.request.TestSettingsRequest;
import at.rtr.rmbt.response.TestSettingsResponse;
import at.rtr.rmbt.service.IntegrityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Test Settings")
@RestController
@RequestMapping(URIConstants.REGISTRATION_URL)
@RequiredArgsConstructor
public class RegistrationController {

    private final TestSettingsFacade testSettingsFacade;
    private final IntegrityService integrityService;

    @Operation(summary = "Update test settings", description = "Request to update configuration for basic test")
    @PostMapping
    public TestSettingsResponse updateTestSettings(@RequestBody TestSettingsRequest testSettingsRequest, HttpServletRequest request, @RequestHeader Map<String, String> headers) {
        // Play Integrity verification runs BEFORE the transactional facade: the Google call
        // must never hold a DB connection from the pool.
        IntegrityCheckOutcome integrityOutcome = integrityService.check(testSettingsRequest);
        if (integrityOutcome != null && integrityOutcome.getAction() == IntegrityAction.REJECTED) {
            return integrityService.buildRejectionResponse(testSettingsRequest.getLanguage());
        }

        TestSettingsResponse testSettingsResponse = testSettingsFacade.updateTestSettings(testSettingsRequest, request, headers);

        if (integrityOutcome != null && integrityOutcome.getRecordUid() != null
                && testSettingsResponse.getTestUuid() != null) {
            integrityService.attachTest(integrityOutcome.getRecordUid(),
                    UUID.fromString(testSettingsResponse.getTestUuid()));
        }
        return testSettingsResponse;
    }
}
