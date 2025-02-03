package ch.admin.bit.jeap.messagecontract.domain.schema;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.stream.Collectors.groupingBy;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageSchemaService {

    private final MessageTypeRepositoryFactory typeRepositoryFactory;

    @Timed(value = "loadschemas.time", description = "Time taken to load message type schemas from the registry", histogram = true)
    public void loadSchemas(List<MessageContract> messageContracts) {
        messageContracts.stream()
                .collect(groupingBy(MessageContract::getRegistryUrl))
                .forEach(this::loadSchemasFromRepository);
    }

    private void loadSchemasFromRepository(String registryGitRepoUrl, List<MessageContract> messageContracts) {
        try (MessageTypeRepository messageTypeRepository = typeRepositoryFactory.cloneRepository(registryGitRepoUrl)) {
            messageContracts.forEach(contract -> loadSchemaForMessageType(messageTypeRepository, contract));
        }
    }

    private void loadSchemaForMessageType(MessageTypeRepository messageTypeRepository, MessageContract messageContract) {
        String schema = getSchema(messageContract, messageTypeRepository);
        messageContract.setAvroProtocolSchema(schema);
    }

    private String getSchema(MessageContract messageContract, MessageTypeRepository repository) {
        log.info("Loading message type schema {}:{}", messageContract.getMessageType(), messageContract.getMessageTypeVersion());
        return repository.getSchemaAsAvroProtocolJson(
                messageContract.getBranch(),
                messageContract.getCommitHash(),
                messageContract.getMessageType(),
                messageContract.getMessageTypeVersion());
    }
}
