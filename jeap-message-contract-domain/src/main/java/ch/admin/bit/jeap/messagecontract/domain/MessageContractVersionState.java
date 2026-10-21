package ch.admin.bit.jeap.messagecontract.domain;

/** Result of comparing a deployed contract's message type version with the latest version in its registry. */
public enum MessageContractVersionState {
    /** The contract uses the latest message type version of the registry's default branch. */
    UP_TO_DATE,
    /** A newer message type version is available on the registry's default branch. */
    OUTDATED,
    /**
     * The latest version could not be determined for this contract, e.g. because its message type is missing from
     * the registry, it has no registry URL or a version is not a valid semantic version.
     */
    UNKNOWN
}
