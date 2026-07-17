package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateCompatibilityService;
import ch.admin.bit.jeap.messagecontract.web.api.dto.RenovateDatasourceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/renovate")
@RequiredArgsConstructor
@Validated
public class RenovateController {

    static final String MAVEN_COORDINATE = "[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*\\.messagetype\\.[A-Za-z0-9_]+:[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*";
    static final String SEMANTIC_VERSION = "(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)";

    private final RenovateCompatibilityService renovateCompatibilityService;

    @GetMapping("/message-types/{packageName}")
    @Operation(summary = "List newer message-type releases compatible with current deployments",
            description = "Omit appName for global mode or supply it for app-specific role/topic checks. " +
                    "Expected incompatibilities and missing counterparts return an empty release list.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Compatible releases, possibly empty"),
                    @ApiResponse(responseCode = "400", description = "Invalid Maven coordinate, version, application, or environment"),
                    @ApiResponse(responseCode = "401", description = "Authentication required or invalid"),
                    @ApiResponse(responseCode = "403", description = "Insufficient role"),
                    @ApiResponse(responseCode = "503", description = "Message type registry temporarily unavailable")
            })
    @PreAuthorize("hasAnyRole('messagecontract-read', 'messagecontract-write', 'messagecontract-contract-upload')")
    public RenovateDatasourceDto compatibleVersions(
            @NotBlank @Pattern(regexp = MAVEN_COORDINATE) @PathVariable
            @Parameter(description = "Strict message-type Maven coordinate groupId:artifactId") String packageName,
            @NotBlank @Pattern(regexp = SEMANTIC_VERSION) @RequestParam
            @Parameter(description = "Current nonnegative semantic version x.y.z") String currentValue,
            @Pattern(regexp = ".*\\S.*") @RequestParam(required = false)
            @Parameter(description = "Application name for app-specific mode; omit for global mode") String appName,
            @NotBlank @RequestParam(defaultValue = "PROD")
            @Parameter(description = "Deployment environment; defaults to PROD") String environment) {
        if (appName != null) {
            return new RenovateDatasourceDto(renovateCompatibilityService.findCompatibleReleasesForApp(
                    appName, packageName, currentValue, environment));
        }
        return new RenovateDatasourceDto(renovateCompatibilityService.findGloballyCompatibleReleases(
                packageName, currentValue, environment));
    }
}
