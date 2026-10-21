package ch.admin.bit.jeap.messagecontract.web.api.dto;

import ch.admin.bit.jeap.messagecontract.domain.MessageContractVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record MessageContractVersionStatusDto(
        String appName,
        String appVersion,
        String messageType,
        String usedVersion,
        @Schema(nullable = true, description = "Latest message type version on the registry's default branch; null if the status is UNKNOWN")
        String latestVersion,
        String topic,
        MessageContractRole role,
        @Schema(description = "True only if the status is UP_TO_DATE")
        boolean upToDate,
        @Schema(description = "UNKNOWN if the latest version of this contract's message type could not be determined; " +
                "the contract is then neither up to date nor known to be outdated")
        MessageContractVersionStateDto status) {

    public static MessageContractVersionStatusDto fromDomainObject(MessageContractVersionStatus status) {
        return new MessageContractVersionStatusDto(
                status.appName(),
                status.appVersion(),
                status.messageType(),
                status.usedVersion(),
                status.latestVersion(),
                status.topic(),
                MessageContractRole.valueOf(status.role().name()),
                status.upToDate(),
                MessageContractVersionStateDto.fromDomainObject(status.state()));
    }
}
