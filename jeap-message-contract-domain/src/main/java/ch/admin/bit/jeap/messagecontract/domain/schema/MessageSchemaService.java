package ch.admin.bit.jeap.messagecontract.domain.schema;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ch.admin.bit.jeap.messagecontract.messagetype.repository.Elapsed.elapsedMs;
import static java.util.stream.Collectors.groupingBy;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageSchemaService {

    private static final Pattern VERSIONED_NAMESPACE_PATTERN = Pattern.compile(".*\\.v(\\d+)");

    private final MessageTypeRepositoryFactory typeRepositoryFactory;

    @Timed(value = "loadschemas.time", description = "Time taken to load message type schemas from the registry", histogram = true)
    public void loadSchemas(List<MessageContract> messageContracts) {
        List<MessageContract> contractsWithoutSchema = messageContracts.stream()
                .filter(contract -> !contract.hasUploadedSchema())
                .toList();
        int uploadedSchemaCount = messageContracts.size() - contractsWithoutSchema.size();

        messageContracts.stream()
                .filter(MessageContract::hasUploadedSchema)
                .forEach(MessageSchemaService::validateUploadedSchema);

        if (contractsWithoutSchema.isEmpty()) {
            log.info("loadSchemas: {} contract(s), all schemas provided in the upload, no registry access needed", uploadedSchemaCount);
            return;
        }

        var byRegistry = contractsWithoutSchema.stream()
                .collect(groupingBy(MessageContract::getRegistryUrl));
        log.info("loadSchemas: {} contract(s), {} with uploaded schema, {} to be loaded from {} registry url(s)",
                messageContracts.size(), uploadedSchemaCount, contractsWithoutSchema.size(), byRegistry.size());
        byRegistry.forEach(this::loadSchemasFromRepository);
    }

    private void loadSchemasFromRepository(String registryGitRepoUrl, List<MessageContract> messageContracts) {
        long startNanos = System.nanoTime();
        try (MessageTypeRepository messageTypeRepository = typeRepositoryFactory.cloneRepository(registryGitRepoUrl)) {
            messageContracts.forEach(contract -> loadSchemaForMessageType(messageTypeRepository, contract));
        }
        log.info("loadSchemasFromRepository: registry={} contractCount={} done in {} ms",
                registryGitRepoUrl, messageContracts.size(), elapsedMs(startNanos));
    }

    private void loadSchemaForMessageType(MessageTypeRepository messageTypeRepository, MessageContract messageContract) {
        long startNanos = System.nanoTime();
        String schema = repositoryGetSchema(messageContract, messageTypeRepository);
        messageContract.setAvroProtocolSchema(schema);
        log.info("loadSchemaForMessageType: messageType={}:{} branch={} commit={} elapsedMs={}",
                messageContract.getMessageType(), messageContract.getMessageTypeVersion(),
                messageContract.getBranch(), messageContract.getCommitHash(),
                elapsedMs(startNanos));
    }

    private String repositoryGetSchema(MessageContract messageContract, MessageTypeRepository repository) {
        return repository.getSchemaAsAvroProtocolJson(
                messageContract.getBranch(),
                messageContract.getCommitHash(),
                messageContract.getMessageType(),
                messageContract.getMessageTypeVersion());
    }

    private static void validateUploadedSchema(MessageContract messageContract) {
        Schema schema;
        try {
            schema = new Schema.Parser().parse(messageContract.getAvroSchema());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("The uploaded avro schema for the message type %s:%s is not a valid avro schema"
                    .formatted(messageContract.getMessageType(), messageContract.getMessageTypeVersion()), ex);
        }
        if (schema.getType() != Schema.Type.RECORD || !messageContract.getMessageType().equals(schema.getName())) {
            throw new IllegalArgumentException(
                    "The uploaded avro schema for the message type %s:%s does not define the record %s but '%s'"
                            .formatted(messageContract.getMessageType(), messageContract.getMessageTypeVersion(),
                                    messageContract.getMessageType(), schema.getFullName()));
        }
        validateUploadedSchemaVersion(messageContract, schema);
    }

    /**
     * Message type versions are declared in the namespace of the avro schema as <code>.v{major}</code> as of major
     * version 2. If the namespace of the uploaded schema follows that convention, it has to match the major version of
     * the message type version of the contract. Namespaces not following the convention are not validated, as the
     * namespace of a message type is not enforced by the message type registry.
     */
    private static void validateUploadedSchemaVersion(MessageContract messageContract, Schema schema) {
        Matcher namespaceMatcher = VERSIONED_NAMESPACE_PATTERN.matcher(schema.getNamespace() == null ? "" : schema.getNamespace());
        if (!namespaceMatcher.matches()) {
            return;
        }
        String schemaMajorVersion = namespaceMatcher.group(1);
        String contractMajorVersion = messageContract.getMessageTypeVersion().split("\\.")[0];
        if (!schemaMajorVersion.equals(contractMajorVersion)) {
            throw new IllegalArgumentException(
                    "The uploaded avro schema '%s' for the message type %s:%s declares the major version %s"
                            .formatted(schema.getFullName(), messageContract.getMessageType(),
                                    messageContract.getMessageTypeVersion(), schemaMajorVersion));
        }
    }
}
