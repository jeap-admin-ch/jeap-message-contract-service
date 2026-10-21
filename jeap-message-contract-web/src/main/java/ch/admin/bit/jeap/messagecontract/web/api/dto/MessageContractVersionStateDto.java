package ch.admin.bit.jeap.messagecontract.web.api.dto;

import ch.admin.bit.jeap.messagecontract.domain.MessageContractVersionState;

public enum MessageContractVersionStateDto {
    UP_TO_DATE,
    OUTDATED,
    UNKNOWN;

    public static MessageContractVersionStateDto fromDomainObject(MessageContractVersionState state) {
        return valueOf(state.name());
    }
}
