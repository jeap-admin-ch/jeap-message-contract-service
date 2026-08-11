package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;

public record MessageContractVersionStatus(
        String appName,
        String appVersion,
        String messageType,
        String usedVersion,
        String latestVersion,
        String topic,
        MessageContractRole role) {

    public boolean upToDate() {
        return usedVersion.equals(latestVersion);
    }
}
