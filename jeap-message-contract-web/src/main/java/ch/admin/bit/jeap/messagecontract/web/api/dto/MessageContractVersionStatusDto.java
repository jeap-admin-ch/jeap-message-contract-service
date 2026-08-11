package ch.admin.bit.jeap.messagecontract.web.api.dto;

import ch.admin.bit.jeap.messagecontract.domain.MessageContractVersionStatus;

public record MessageContractVersionStatusDto(
        String appName,
        String appVersion,
        String messageType,
        String usedVersion,
        String latestVersion,
        String topic,
        MessageContractRole role,
        boolean upToDate) {

    public static MessageContractVersionStatusDto fromDomainObject(MessageContractVersionStatus status) {
        return new MessageContractVersionStatusDto(
                status.appName(),
                status.appVersion(),
                status.messageType(),
                status.usedVersion(),
                status.latestVersion(),
                status.topic(),
                MessageContractRole.valueOf(status.role().name()),
                status.upToDate());
    }
}
