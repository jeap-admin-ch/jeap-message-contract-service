package ch.admin.bit.jeap.messagecontract.web.api.dto;

public enum CompatibilityMode {
    BACKWARD,
    BACKWARD_TRANSITIVE,
    FORWARD,
    FORWARD_TRANSITIVE,
    FULL,
    FULL_TRANSITIVE,
    NONE;

    public ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode toDomainObject() {
        return ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode.valueOf(name());
    }

    public static CompatibilityMode fromDomainObject(ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode compatibilityMode) {
        return valueOf(compatibilityMode.name());
    }
}
