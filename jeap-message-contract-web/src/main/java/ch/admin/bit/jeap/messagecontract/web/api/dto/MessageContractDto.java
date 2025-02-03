package ch.admin.bit.jeap.messagecontract.web.api.dto;

import ch.admin.bit.jeap.messagecontract.persistence.MessageContractInfo;

public record MessageContractDto(
        String appName,
        String appVersion,
        String messageType,
        String messageTypeVersion,
        String topic,
        MessageContractRole role,
        String registryUrl,
        String commitHash,
        String branch,
        CompatibilityMode compatibilityMode,
        String encryptionKeyId) {

    public static MessageContractDto fromDomainObject(MessageContractInfo contractInfo) {
        return new MessageContractDto(contractInfo.getAppName(),
                contractInfo.getAppVersion(),
                contractInfo.getMessageType(),
                contractInfo.getMessageTypeVersion(),
                contractInfo.getTopic(),
                MessageContractRole.valueOf(contractInfo.getRole()),
                contractInfo.getRegistryUrl(),
                contractInfo.getCommitHash(),
                contractInfo.getBranch(),
                CompatibilityMode.valueOf(contractInfo.getCompatibilityMode()),
                contractInfo.getEncryptionKeyId());
    }

}
