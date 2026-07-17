package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateCompatibilityService;
import ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateRelease;
import ch.admin.bit.jeap.messagecontract.web.api.dto.RenovateDatasourceDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenovateControllerTest {

    private static final String PACKAGE_NAME = "ch.admin.bit.jeap.messagetype.test:test-event";

    @Mock
    private RenovateCompatibilityService compatibilityService;

    @InjectMocks
    private RenovateController controller;

    @Test
    void usesGlobalCheckWithoutAppName() {
        List<RenovateRelease> releases = List.of(new RenovateRelease("1.1.0"));
        when(compatibilityService.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD"))
                .thenReturn(releases);

        RenovateDatasourceDto result = controller.compatibleVersions(PACKAGE_NAME, "1.0.0", null, "PROD");

        assertThat(result.releases()).isEqualTo(releases);
        verify(compatibilityService).findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");
    }

    @Test
    void usesAppSpecificCheckWithAppName() {
        List<RenovateRelease> releases = List.of(new RenovateRelease("1.1.0"));
        when(compatibilityService.findCompatibleReleasesForApp("my-app", PACKAGE_NAME, "1.0.0", "PROD"))
                .thenReturn(releases);

        RenovateDatasourceDto result = controller.compatibleVersions(PACKAGE_NAME, "1.0.0", "my-app", "PROD");

        assertThat(result.releases()).isEqualTo(releases);
        verify(compatibilityService).findCompatibleReleasesForApp("my-app", PACKAGE_NAME, "1.0.0", "PROD");
    }

    @Test
    void presentAppNameAlwaysUsesAppSpecificMode() {
        when(compatibilityService.findCompatibleReleasesForApp(" ", PACKAGE_NAME, "1.0.0", "PROD"))
                .thenReturn(List.of());

        controller.compatibleVersions(PACKAGE_NAME, "1.0.0", " ", "PROD");

        verify(compatibilityService).findCompatibleReleasesForApp(" ", PACKAGE_NAME, "1.0.0", "PROD");
    }
}
