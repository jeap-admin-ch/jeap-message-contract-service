package ch.admin.bit.jeap.messagecontract.domain;

/** Signals that a complete deployed message-version status snapshot could not be resolved. */
public class MessageContractVersionStatusUnavailableException extends RuntimeException {
    public MessageContractVersionStatusUnavailableException(String message) {
        super(message);
    }

    public MessageContractVersionStatusUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
