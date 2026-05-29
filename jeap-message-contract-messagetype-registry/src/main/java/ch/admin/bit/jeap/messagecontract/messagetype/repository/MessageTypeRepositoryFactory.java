package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.github.GitHubMessageTypeRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Optional;

import static ch.admin.bit.jeap.messagecontract.messagetype.repository.Elapsed.elapsedMs;
import static ch.admin.bit.jeap.messagecontract.messagetype.repository.RepositoryProperties.RepositoryType.GITHUB;

@Component
@Slf4j
public class MessageTypeRepositoryFactory {

    private final MessageTypeRepositoryProperties properties;
    private final MeterRegistry meterRegistry;
    private final MessageTypeRepositoryReferenceCache referenceCache;

    @Autowired
    public MessageTypeRepositoryFactory(MessageTypeRepositoryProperties properties,
                                        MeterRegistry meterRegistry,
                                        MessageTypeRepositoryReferenceCache referenceCache) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.referenceCache = referenceCache;
    }

    public MessageTypeRepositoryFactory(MessageTypeRepositoryProperties properties, MeterRegistry meterRegistry) {
        this(properties, meterRegistry, null);
    }

    @Timed(value = "clonerepository.time", description = "Time taken to load message type schemas from the registry", histogram = true)
    public MessageTypeRepository cloneRepository(String gitUri) {
        long startNanos = System.nanoTime();
        MessageTypeRepository messageTypeRepository = new MessageTypeRepository(gitUri); // NOSONAR close is ensured by the method's client
        if (properties != null && properties.getRepositories() != null) {
            Optional<RepositoryProperties> knownRepository = properties.getRepositories().stream().filter(repository -> repository.getUri().equals(gitUri)).findFirst();
            if (knownRepository.isPresent() && GITHUB.equals(knownRepository.get().getType())) {
                messageTypeRepository = new GitHubMessageTypeRepository(gitUri, knownRepository.get().getParameters(), meterRegistry);
            }
        }
        boolean cacheHit = false;
        if (referenceCache != null) {
            Optional<File> cacheRepoDir = referenceCache.getCacheRepoDir(gitUri);
            if (cacheRepoDir.isPresent()) {
                messageTypeRepository.setReferenceCacheDir(cacheRepoDir.get());
                messageTypeRepository.setCacheMissRefresh(() -> referenceCache.refreshOne(gitUri));
                messageTypeRepository.setEagerCacheRefresh(() -> referenceCache.refreshIfStale(gitUri));
                cacheHit = true;
            }
        }
        messageTypeRepository.cloneGitRepo();
        log.info("Cloned {} in {} ms (cacheHit={})", gitUri, elapsedMs(startNanos), cacheHit);
        return messageTypeRepository;
    }
}
