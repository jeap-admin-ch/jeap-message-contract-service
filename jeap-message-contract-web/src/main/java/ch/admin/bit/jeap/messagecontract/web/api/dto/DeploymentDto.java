package ch.admin.bit.jeap.messagecontract.web.api.dto;

import ch.admin.bit.jeap.messagecontract.persistence.model.Deployment;

import java.time.ZonedDateTime;

public record DeploymentDto(
        String appName,
        String appVersion,
        String environment,
        ZonedDateTime createdAt) {

    public static DeploymentDto fromDomainObject(Deployment deployment) {
        return new DeploymentDto(deployment.getAppName(),
                deployment.getAppVersion(),
                deployment.getEnvironment(),
                deployment.getCreatedAt());
    }

}
