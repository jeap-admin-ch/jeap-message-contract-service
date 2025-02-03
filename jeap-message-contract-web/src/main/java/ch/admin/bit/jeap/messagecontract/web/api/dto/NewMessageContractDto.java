package ch.admin.bit.jeap.messagecontract.web.api.dto;

import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NewMessageContractDto(
        @NotBlank String messageType,
        @NotBlank String messageTypeVersion,
        @NotBlank String topic,
        @NotNull MessageContractRole role,
        @NotNull String registryUrl,
        String commitHash,
        String branch,
        @NotNull CompatibilityMode compatibilityMode,
        String encryptionKeyId) {

    public MessageContract toNewDomainObject(String appName, String appVersion, String transactionId) {
        return MessageContract.builder()
                .appName(appName)
                .appVersion(appVersion)
                .messageType(messageType)
                .messageTypeVersion(messageTypeVersion)
                .topic(topic)
                .role(role.toDomainObject())
                .registryUrl(registryUrl)
                .commitHash(commitHash)
                .branch(branch)
                .compatibilityMode(compatibilityMode.toDomainObject())
                .encryptionKeyId(encryptionKeyId)
                .transactionId(transactionId)
                .build();
    }
}
