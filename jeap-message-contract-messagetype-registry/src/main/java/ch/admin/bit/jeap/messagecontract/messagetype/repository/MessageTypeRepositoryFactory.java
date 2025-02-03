package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import io.micrometer.core.annotation.Timed;
import org.springframework.stereotype.Component;

@Component
public class MessageTypeRepositoryFactory {

    @Timed(value = "clonerepository.time", description = "Time taken to load message type schemas from the registry", histogram = true)
    public MessageTypeRepository cloneRepository(String gitUri) {
        return new MessageTypeRepository(gitUri);
    }
}
