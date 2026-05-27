package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.github.GitHubMessageTypeRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static ch.admin.bit.jeap.messagecontract.messagetype.repository.RepositoryProperties.RepositoryType.GITHUB;

@AllArgsConstructor
@Component
public class MessageTypeRepositoryFactory {

    private final MessageTypeRepositoryProperties properties;
    private final MeterRegistry meterRegistry;

    @Timed(value = "clonerepository.time", description = "Time taken to load message type schemas from the registry", histogram = true)
    public MessageTypeRepository cloneRepository(String gitUri) {
        MessageTypeRepository messageTypeRepository = new MessageTypeRepository(gitUri); // NOSONAR close is ensured by the method's client
        if (properties != null && properties.getRepositories() != null) {
            Optional<RepositoryProperties> knownRepository = properties.getRepositories().stream().filter(repository -> repository.getUri().equals(gitUri)).findFirst();
            if (knownRepository.isPresent() && GITHUB.equals(knownRepository.get().getType())) {
                messageTypeRepository = new GitHubMessageTypeRepository(gitUri, knownRepository.get().getParameters(), meterRegistry);
            }
        }
        messageTypeRepository.cloneGitRepo();
        return messageTypeRepository;
    }
}
