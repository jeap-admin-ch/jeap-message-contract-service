package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.web.api.dto.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.web.api.dto.MessageContractRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LegacyNewMessageContractDto(
        @NotBlank String messageType,
        @NotBlank String messageTypeVersion,
        @NotBlank String topic,
        @NotNull MessageContractRole role,
        @NotNull String registryUrl,
        String commitHash,
        String branch,
        @NotNull CompatibilityMode compatibilityMode) {
}
