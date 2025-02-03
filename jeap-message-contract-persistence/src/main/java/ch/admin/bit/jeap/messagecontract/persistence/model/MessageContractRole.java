package ch.admin.bit.jeap.messagecontract.persistence.model;

public enum MessageContractRole {
    CONSUMER,
    PRODUCER;

    public MessageContractRole opposite() {
        return this == CONSUMER ? PRODUCER : CONSUMER;
    }
}
