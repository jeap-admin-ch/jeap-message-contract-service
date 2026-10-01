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
        String encryptionKeyId,
        String schema) {

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
                .avroSchema(schemaOrNullIfBlank())
                .transactionId(transactionId)
                .build();
    }

    private String schemaOrNullIfBlank() {
        return schema == null || schema.isBlank() ? null : schema;
    }

    /**
     * The schema is replaced by its size, as a message type schema can be several kilobytes in size and contracts are
     * logged on every upload.
     */
    @Override
    public String toString() {
        return ("NewMessageContractDto[messageType=%s, messageTypeVersion=%s, topic=%s, role=%s, registryUrl=%s, " +
                "commitHash=%s, branch=%s, compatibilityMode=%s, encryptionKeyId=%s, schema=%s]")
                .formatted(messageType, messageTypeVersion, topic, role, registryUrl, commitHash, branch,
                        compatibilityMode, encryptionKeyId, schemaForLogging());
    }

    private String schemaForLogging() {
        return schema == null ? null : "<" + schema.length() + " chars>";
    }
}
