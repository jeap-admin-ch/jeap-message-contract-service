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

import static ch.admin.bit.jeap.messagecontract.messagetype.repository.Elapsed.elapsedMs;
import static java.util.stream.Collectors.groupingBy;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageSchemaService {

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
        try {
            new Schema.Parser().parse(messageContract.getAvroSchema());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("The uploaded avro schema for the message type %s:%s is not a valid avro schema"
                    .formatted(messageContract.getMessageType(), messageContract.getMessageTypeVersion()), ex);
        }
    }
}
