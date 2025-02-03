package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.domain.DeploymentService;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityService;
import ch.admin.bit.jeap.messagecontract.persistence.model.Deployment;
import ch.admin.bit.jeap.messagecontract.web.api.dto.DeploymentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

@RestController
@RequestMapping("/api/deployments")
@RequiredArgsConstructor
@Slf4j
@Validated
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final CompatibilityService compatibilityService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List last 10 deployments")
    public List<DeploymentDto> findLast10Deployments() {
        return deploymentService.findLast10Deployments().stream()
                .map(DeploymentDto::fromDomainObject)
                .toList();
    }

    @PutMapping(path = "/{appName}/{appVersion}/{environment}")
    @Operation(summary = "Register deployment for an app version on a environment", responses = {
            @ApiResponse(responseCode = "201", description = "Deployment successfully registered"),
            @ApiResponse(responseCode = "200", description = "Deployment ignored because appName and/or appVersion are unknown")
    })
    @PreAuthorize("hasAnyRole('messagecontract-write', 'messagecontract-contract-upload')")
    public ResponseEntity<String> registerNewDeployment(@NotBlank @PathVariable String appName,
            @NotBlank @PathVariable String appVersion,
            @NotBlank @PathVariable String environment) {
        log.info("Save new Deployment: app={}:{} environment={}", appName, appVersion, environment);
        final Deployment deployment = deploymentService.saveNewDeployment(appName, appVersion, environment.toUpperCase());

        if (deployment != null) {
            return ResponseEntity.status(HttpStatus.CREATED).body("Deployment successfully registered");
        } else {
            return ResponseEntity.status(HttpStatus.OK).body("Deployment ignored because appName and/or appVersion are unknown");
        }
    }

    @DeleteMapping(path = "/{appName}/{environment}")
    @Operation(summary = "Deletes a deployment of an app on a environment", responses = {
            @ApiResponse(responseCode = "200", description = "Deployment deleted")
    })
    @PreAuthorize("hasRole('messagecontract-write')")
    public ResponseEntity<String> deleteDeployment(@NotBlank @PathVariable("appName") String appName,
                                                   @NotBlank @PathVariable("environment") String environment) {
        log.info("Deleting Deployment: app={} environment={}", appName, environment);
        deploymentService.deleteDeployment(appName, environment.toUpperCase());
        return ResponseEntity.status(HttpStatus.OK).body("Deployment successfully deleted");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/compatibility/{appName}/{appVersion}/{environment}")
    @Operation(summary = "Get compatibility of a planned deployment regarding the app's contracts with consumer/producer versions on the environment", responses = {
            @ApiResponse(responseCode = "200", description = "App version is compatible with consumers/producers on environment"),
            @ApiResponse(responseCode = "412", description = "App version is not compatible with consumers/producers on environment, schema incompatibilities have been detected")
    })
    @PreAuthorize("hasAnyRole('messagecontract-read', 'messagecontract-write', 'messagecontract-contract-upload')")
    public ResponseEntity<CompatibilityCheckResult> getCompatibility(@NotBlank @PathVariable String appName,
            @NotBlank @PathVariable String appVersion,
            @NotBlank @PathVariable String environment) {
        CompatibilityCheckResult compatibilityCheckResult = compatibilityService.checkCompatibility(appName, appVersion, environment.toUpperCase());
        log.info("Compatibility check result for {}:{} on {}: {}", appName, appVersion, environment, compatibilityCheckResult.compatible());
        if (compatibilityCheckResult.compatible()) {
            return ResponseEntity.ok(compatibilityCheckResult);
        }
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(compatibilityCheckResult);
    }
}
