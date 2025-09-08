package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.github.GitHubMessageTypeRepository;
import io.micrometer.core.annotation.Timed;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static ch.admin.bit.jeap.messagecontract.messagetype.repository.RepositoryProperties.RepositoryType.GITHUB;

@AllArgsConstructor
@Component
public class MessageTypeRepositoryFactory {

    private final MessageTypeRepositoryProperties properties;

    @Timed(value = "clonerepository.time", description = "Time taken to load message type schemas from the registry", histogram = true)
    public MessageTypeRepository cloneRepository(String gitUri) {
        MessageTypeRepository messageTypeRepository = new MessageTypeRepository(gitUri);
        if (properties != null && properties.getRepositories() != null) {
            Optional<RepositoryProperties> knownRepository = properties.getRepositories().stream().filter(repository -> repository.getUri().equals(gitUri)).findFirst();
            if (knownRepository.isPresent() && GITHUB.equals(knownRepository.get().getType())) {
                messageTypeRepository = new GitHubMessageTypeRepository(gitUri, knownRepository.get().getParameters());
            }
        }
        messageTypeRepository.cloneGitRepo();
        return messageTypeRepository;
    }
}
