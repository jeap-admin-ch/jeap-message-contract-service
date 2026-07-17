package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateCompatibilityService;
import ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateRegistryException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RenovateControllerHttpTest extends ControllerTestBase {

    private static final String PATH = "/api/renovate/message-types/{packageName}";
    private static final String PACKAGE_NAME = "ch.admin.bit.jeap.messagetype.test:test-event";

    @MockitoBean
    private RenovateCompatibilityService compatibilityService;

    @Autowired
    RenovateControllerHttpTest(MockMvc mockMvc) {
        super(mockMvc);
    }

    @Test
    void omittedAppNameSelectsGlobalModeAndEnvironmentIsNotRestrictedToProd() throws Exception {
        when(compatibilityService.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "REF"))
                .thenReturn(java.util.List.of());

        mockMvc.perform(validRequest().param("environment", "REF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releases").isArray());

        verify(compatibilityService).findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "REF");
    }

    @Test
    void unknownNonblankAppRemainsSafeEmpty() throws Exception {
        when(compatibilityService.findCompatibleReleasesForApp("unknown", PACKAGE_NAME, "1.0.0", "PROD"))
                .thenReturn(java.util.List.of());

        mockMvc.perform(validRequest().param("appName", "unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releases").isEmpty());
    }

    @Test
    void presentBlankAppNameIsBadRequestAndNeverGlobal() throws Exception {
        mockMvc.perform(validRequest().param("appName", " ")).andExpect(status().isBadRequest());

        verifyNoInteractions(compatibilityService);
    }

    @Test
    void invalidCoordinateIsBadRequest() throws Exception {
        mockMvc.perform(get(PATH, "not-a-coordinate").param("currentValue", "1.0.0")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(compatibilityService);
    }

    @Test
    void invalidCurrentVersionIsBadRequest() throws Exception {
        mockMvc.perform(get(PATH, PACKAGE_NAME).param("currentValue", "-1.0.0")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(compatibilityService);
    }

    @Test
    void blankEnvironmentIsBadRequest() throws Exception {
        mockMvc.perform(validRequest().param("environment", " ")).andExpect(status().isBadRequest());
        verifyNoInteractions(compatibilityService);
    }

    @Test
    void registryInfrastructureFailureIsServiceUnavailable() throws Exception {
        when(compatibilityService.findGloballyCompatibleReleases(anyString(), anyString(), anyString()))
                .thenThrow(new RenovateRegistryException("unavailable", new IllegalStateException("clone failed")));

        mockMvc.perform(validRequest()).andExpect(status().isServiceUnavailable());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest() {
        return get(PATH, PACKAGE_NAME).param("currentValue", "1.0.0")
                .with(httpBasic("read", "secret"));
    }
}
