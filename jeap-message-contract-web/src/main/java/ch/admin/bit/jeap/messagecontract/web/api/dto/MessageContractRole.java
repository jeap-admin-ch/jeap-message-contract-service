package ch.admin.bit.jeap.messagecontract.web.api.dto;

public enum MessageContractRole {
    CONSUMER,
    PRODUCER;

    public ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole toDomainObject() {
        return ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole.valueOf(name());
    }

    public static MessageContractRole fromDomainObject(ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole role) {
        return valueOf(role.name());
    }
}
