package ch.admin.bit.jeap.messagecontract.domain.renovate;

import ch.admin.bit.jeap.messagecontract.domain.schema.MessageSchemaService;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.DeployedMessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractSchema;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateCacheConfiguration.*;

/**
 * Loads the data used to answer Renovate datasource requests.
 * <p>
 * Renovate queries many message types for many repositories at once. The results are therefore reused for
 * {@link RenovateProperties#getCacheTtl()}, so that such a burst neither repeats the same database queries nor reads
 * the registry again for every request. Concurrent requests for the same missing entry wait for a single load. A
 * failed load is not cached, and its exception reaches the caller unchanged. The cached values are immutable or, like
 * the candidate schema, not shared with the caller's mutable objects.
 * <p>
 * The caching is applied by the Spring proxy of this bean, so the methods only cache when called from another bean.
 */
@Component
class RenovateLookups {

    private final MessageContractRepository contractRepository;
    private final MessageTypeRepositoryFactory repositoryFactory;
    private final MessageSchemaService schemaService;

    RenovateLookups(MessageContractRepository contractRepository,
                    MessageTypeRepositoryFactory repositoryFactory,
                    MessageSchemaService schemaService) {
        this.contractRepository = contractRepository;
        this.repositoryFactory = repositoryFactory;
        this.schemaService = schemaService;
    }

    @Cacheable(cacheNames = DEPLOYED_CONTRACTS, key = "#a0", sync = true, cacheManager = CACHE_MANAGER)
    public List<DeployedMessageContract> deployedContracts(DeployedContractsKey key) {
        return List.copyOf(contractRepository.findCurrentlyDeployedContracts(
                key.environment(), key.normalizedMessageType()));
    }

    @Cacheable(cacheNames = CONTRACT_SCHEMAS, key = "#a0", sync = true, cacheManager = CACHE_MANAGER)
    public Map<UUID, MessageContractSchema> contractSchemas(Set<UUID> ids) {
        return contractRepository.findSchemas(ids).stream()
                .collect(Collectors.toUnmodifiableMap(MessageContractSchema::id, Function.identity()));
    }

    @Cacheable(cacheNames = REGISTRY_SNAPSHOTS, key = "#a0", sync = true, cacheManager = CACHE_MANAGER)
    public MessageTypeRepository.MessageTypeSnapshot registrySnapshot(SnapshotKey key) {
        try (MessageTypeRepository typeRepository = repositoryFactory.cloneRepository(key.registryUrl())) {
            return typeRepository.getMessageTypeSnapshot(key.branch(), key.messageType(), key.definingSystem());
        }
    }

    /**
     * The candidate schema is read at the immutable snapshot commit, so the same candidate always has the same schema.
     * Only the schema is cached: the candidate is built anew for every request.
     *
     * @param key       identifies the candidate, see {@link CandidateSchemaKey#of(MessageContract)}
     * @param candidate the candidate whose schema is loaded on a cache miss
     * @return the avro protocol schema of the candidate
     */
    @Cacheable(cacheNames = CANDIDATE_SCHEMAS, key = "#a0", sync = true, cacheManager = CACHE_MANAGER)
    public String candidateSchema(CandidateSchemaKey key, MessageContract candidate) {
        schemaService.loadSchemas(List.of(candidate));
        return candidate.getAvroProtocolSchema();
    }

    record DeployedContractsKey(String environment, String normalizedMessageType) {
    }

    record SnapshotKey(String registryUrl, String branch, String messageType, String definingSystem) {
        static SnapshotKey of(DeployedMessageContract registryContract, String definingSystem) {
            return new SnapshotKey(registryContract.registryUrl(), registryContract.branch(),
                    registryContract.messageType(), definingSystem);
        }
    }

    record CandidateSchemaKey(String registryUrl, String branch, String commitHash, String messageType,
                              String messageTypeVersion) {
        static CandidateSchemaKey of(MessageContract candidate) {
            return new CandidateSchemaKey(candidate.getRegistryUrl(), candidate.getBranch(), candidate.getCommitHash(),
                    candidate.getMessageType(), candidate.getMessageTypeVersion());
        }
    }
}
