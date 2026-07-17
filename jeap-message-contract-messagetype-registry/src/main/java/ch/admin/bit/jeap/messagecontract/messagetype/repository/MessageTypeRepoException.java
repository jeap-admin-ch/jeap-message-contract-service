package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import java.io.File;
import java.util.function.Supplier;

public class MessageTypeRepoException extends RuntimeException {
    private final boolean infrastructureFailure;

    private MessageTypeRepoException(String message) {
        super(message);
        this.infrastructureFailure = false;
    }

    private MessageTypeRepoException(String message, Throwable cause, boolean infrastructureFailure) {
        super(message, cause);
        this.infrastructureFailure = infrastructureFailure;
    }

    static MessageTypeRepoException cloneFailed(String gitUri, Throwable cause) {
        return new MessageTypeRepoException("Cannot clone message type repository %s".formatted(gitUri), cause, true);
    }

    static MessageTypeRepoException missingDescriptor(String path) {
        String message = "Message type directory %s does not contain a JSON descriptor".formatted(path);
        return new MessageTypeRepoException(message);
    }

    static MessageTypeRepoException descriptorParsingFailed(String path, Throwable cause) {
        String message = "Failed to parse message type descriptor " + path;
        return new MessageTypeRepoException(message, cause, false);
    }

    static MessageTypeRepoException schemaLoadingFailed(String schemaFile, Exception ex, boolean infrastructureFailure) {
        return new MessageTypeRepoException(
                "Failed to load schema from file %s".formatted(schemaFile),
                ex, infrastructureFailure);
    }

    static MessageTypeRepoException invalidRepositoryStructure(String path) {
        return new MessageTypeRepoException("Message type repository does not contain a readable directory " + path);
    }

    static MessageTypeRepoException missingSchema(String schemaName, File messageTypeDir, File systemCommonDir, File rootCommonDir) {
        return new MessageTypeRepoException("Cannot find avro schema %s at %s or %s or %s".formatted(
                schemaName, messageTypeDir, systemCommonDir, rootCommonDir));
    }

    static Supplier<MessageTypeRepoException> messageTypeNotFound(String messageTypeName) {
        return () -> new MessageTypeRepoException(
                "Message type %s not found in message type registry".formatted(messageTypeName));
    }

    static Supplier<MessageTypeRepoException> messageTypeVersionNotFound(String messageTypeName, String messageTypeVersion) {
        return () -> new MessageTypeRepoException(
                "Version %s for message type %s not found in message type descriptor".formatted(messageTypeVersion, messageTypeName));
    }

    public static MessageTypeRepoException checkoutFailed(String branch, String commitReference, Exception ex) {
        return new MessageTypeRepoException(
                "Failed to checkout branch %s or commit %s".formatted(branch, commitReference), ex, true);
    }

    public boolean isInfrastructureFailure() {
        return infrastructureFailure;
    }
}
