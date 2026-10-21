package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;

/**
 * Version status of a deployed message contract. {@code latestVersion} is {@code null} if the state is
 * {@link MessageContractVersionState#UNKNOWN}.
 */
public record MessageContractVersionStatus(
        String appName,
        String appVersion,
        String messageType,
        String usedVersion,
        String latestVersion,
        String topic,
        MessageContractRole role,
        MessageContractVersionState state) {

    static MessageContractVersionStatus resolved(String appName, String appVersion, String messageType,
                                                 String usedVersion, String latestVersion, String topic,
                                                 MessageContractRole role) {
        MessageContractVersionState state = usedVersion.equals(latestVersion)
                ? MessageContractVersionState.UP_TO_DATE
                : MessageContractVersionState.OUTDATED;
        return new MessageContractVersionStatus(appName, appVersion, messageType, usedVersion, latestVersion, topic,
                role, state);
    }

    static MessageContractVersionStatus unknown(String appName, String appVersion, String messageType,
                                                String usedVersion, String topic, MessageContractRole role) {
        return new MessageContractVersionStatus(appName, appVersion, messageType, usedVersion, null, topic, role,
                MessageContractVersionState.UNKNOWN);
    }

    public boolean upToDate() {
        return state == MessageContractVersionState.UP_TO_DATE;
    }
}
