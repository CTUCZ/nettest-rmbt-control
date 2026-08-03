package at.rtr.rmbt.controller;

import at.rtr.rmbt.TestUtils;
import at.rtr.rmbt.advice.RtrAdvice;
import at.rtr.rmbt.enums.*;
import at.rtr.rmbt.facade.TestSettingsFacade;
import at.rtr.rmbt.request.TestSettingsRequest;
import at.rtr.rmbt.response.TestSettingsResponse;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
public class RegistrationControllerTest {

    private MockMvc mockMvc;

    @MockitoBean
    private TestSettingsFacade testSettingsFacade;

    @MockitoBean
    private at.rtr.rmbt.service.IntegrityService integrityService;

    @Before
    public void setUp() {
        RegistrationController registrationController = new RegistrationController(testSettingsFacade, integrityService);
        mockMvc = MockMvcBuilders.standaloneSetup(registrationController)
            .setControllerAdvice(new RtrAdvice())
            .build();
    }

    @Test
    public void updateTestSettings_whenCommonRequest_shouldReturnUpdatedSettings() throws Exception {
        when(integrityService.check(any())).thenReturn(null);

        TestSettingsRequest testSettingsRequest = new TestSettingsRequest(
            TestPlatform.ANDROID,
            14.1,
            "myTag",
            false,
            1,
            "dramltexxx",
            "9(G950FXXU5DSFB)",
            "SM-G950F",
            "28",
            false,
            "END",
            1,
            "fix/rtr_release_fixes_'4ce8bda9'",
            "4.1.19",
            true,
            "1",
            3,
            TestSettingsRequest.ProtocolVersion.IPV4,
            new TestSettingsRequest.Location(1.0, 1.0, "provider", 1f, 1.0, 1L, 1L, 1f, 1f, false, 1),
            System.currentTimeMillis(),
            "Europe/Vienna",
            ServerType.RMBT,
            "1",
            ClientType.MOBILE,
            "41ab60bd-becf-45c8-abbc-0e85b59d65ca",
            "en",
            true,
            new TestSettingsRequest.LoopModeInfo(1L, "f46b1165-2451-4989-a2f5-5eb7b598aa48", "c94e7c39-8774-4210-8be9-2411c5da9ff7", 30, 2, 10000, 1, -1, "a165c0a4-cc23-4e39-a1b3-8a111a32e755",null),
            new TestSettingsRequest.Capabilities(
                new TestSettingsRequest.Capabilities.ClassificationCapabilities(1),
                new TestSettingsRequest.Capabilities.QosCapabilities(true),
                true
            ),
            Collections.emptyList(),
            MeasurementType.DEDICATED,
            null,   // referrer
            null,   // integrityToken
            null,   // integrityTimestamp
            null,   // integrityError
            null    // integrityErrorDetail
        );

        TestSettingsResponse testSettingsResponse = new TestSettingsResponse(
            "127.0.0.1",
            "41ab60bd-becf-45c8-abbc-0e85b59d65ca",
            "https://test-server.rtr.com/test",
            "https://test-server.rtr.com/testQoS",
            "1000",
            "OpenRMBT Server",
            0,
            "dev-rmbt.rtr.com",
            "3",
            22,
            "5bd11dd8-992a-4429-b1e0-e93da81e5118",
            ServerType.RMBT,
            true,
            "test_token",
            "5",
            "a165c0a4-cc23-4e39-a1b3-8a111a32e755",
            "provider",
            null,
            Lists.emptyList()
        );

        when(testSettingsFacade.updateTestSettings(eq(testSettingsRequest), any(), any())).thenReturn(testSettingsResponse);

        mockMvc.perform(
            post("/testRequest")
                .content(TestUtils.asJsonString(testSettingsRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("utf-8")
        ).andExpect(status().isOk())
            .andExpect(content().json(TestUtils.asJsonString(testSettingsResponse)));

        verify(testSettingsFacade).updateTestSettings(eq(testSettingsRequest), any(), any());
    }

    @Test
    public void updateTestSettings_whenIntegrityRejected_shouldReturnRejectionWithoutCallingFacade() throws Exception {
        // Given
        at.rtr.rmbt.dto.IntegrityCheckOutcome rejected = at.rtr.rmbt.dto.IntegrityCheckOutcome.builder()
            .recordUid(42L)
            .action(at.rtr.rmbt.enums.IntegrityAction.REJECTED)
            .build();
        when(integrityService.check(any())).thenReturn(rejected);
        TestSettingsResponse rejection = TestSettingsResponse.builder()
            .errorList(java.util.List.of("rejected"))
            .errorFlags(java.util.List.of("TEST_REJECTED"))
            .build();
        when(integrityService.buildRejectionResponse(eq("en"))).thenReturn(rejection);

        // When / Then
        mockMvc.perform(
            post("/testRequest")
                .content("{\"language\":\"en\",\"integrity_token\":\"bad\",\"uuid\":\"41ab60bd-becf-45c8-abbc-0e85b59d65ca\"}")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("utf-8")
        ).andExpect(status().isOk())
            .andExpect(content().json("{\"error\":[\"rejected\"],\"error_flags\":[\"TEST_REJECTED\"]}"));

        org.mockito.Mockito.verifyNoInteractions(testSettingsFacade);
    }

    @Test
    public void updateTestSettings_whenIntegrityAllowedWithRecord_shouldAttachTest() throws Exception {
        // Given
        at.rtr.rmbt.dto.IntegrityCheckOutcome allowed = at.rtr.rmbt.dto.IntegrityCheckOutcome.builder()
            .recordUid(42L)
            .action(at.rtr.rmbt.enums.IntegrityAction.ALLOWED)
            .build();
        when(integrityService.check(any())).thenReturn(allowed);
        TestSettingsResponse response = TestSettingsResponse.builder()
            .testUuid("8c8946bb-e251-42f8-b0d1-43f972c2e216")
            .build();
        when(testSettingsFacade.updateTestSettings(any(), any(), any())).thenReturn(response);

        // When
        mockMvc.perform(
            post("/testRequest")
                .content("{\"language\":\"en\",\"integrity_token\":\"ok\",\"uuid\":\"41ab60bd-becf-45c8-abbc-0e85b59d65ca\"}")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("utf-8")
        ).andExpect(status().isOk());

        // Then
        verify(integrityService).attachTest(eq(42L), eq(java.util.UUID.fromString("8c8946bb-e251-42f8-b0d1-43f972c2e216")));
    }
}
