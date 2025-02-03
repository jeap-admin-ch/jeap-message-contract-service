package ch.admin.bit.jeap.messagecontract.persistence.model;

public enum CompatibilityMode {
    BACKWARD,
    BACKWARD_TRANSITIVE,
    FORWARD,
    FORWARD_TRANSITIVE,
    FULL,
    FULL_TRANSITIVE,
    NONE
}
