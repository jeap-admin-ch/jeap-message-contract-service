package ch.admin.bit.jeap.messagecontract.domain.renovate;

import ch.admin.bit.jeap.messagecontract.domain.SemanticVersion;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.SchemaCompatibilityService;
import ch.admin.bit.jeap.messagecontract.domain.schema.MessageSchemaService;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepoException;
import ch.admin.bit.jeap.messagecontract.persistence.DeployedMessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractSchema;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Answers Renovate datasource requests with the newer message type versions that are compatible with the currently
 * deployed contracts.
 * <p>
 * Renovate queries many message types for many repositories at once. The deployed contracts of a message type and
 * environment, the schemas of the compared contracts and the message type registry data are therefore reused for
 * {@link RenovateProperties#getCacheTtl()}, so that such a burst neither repeats the same database queries nor reads
 * the registry again for every request.
 */
@Component
@Slf4j
public class RenovateCompatibilityService {

    private final MessageContractRepository contractRepository;
    private final MessageTypeRepositoryFactory repositoryFactory;
    private final MessageSchemaService schemaService;
    private final SchemaCompatibilityService schemaCompatibilityService;
    private final ExpiringCache<DeployedContractsKey, List<DeployedMessageContract>> deployedContractsCache;
    private final ExpiringCache<Set<UUID>, Map<UUID, MessageContractSchema>> contractSchemasCache;
    private final ExpiringCache<SnapshotKey, MessageTypeRepository.MessageTypeSnapshot> snapshotCache;
    private final ExpiringCache<CandidateSchemaKey, String> candidateSchemaCache;

    @Autowired
    public RenovateCompatibilityService(MessageContractRepository contractRepository,
                                        MessageTypeRepositoryFactory repositoryFactory,
                                        MessageSchemaService schemaService,
                                        SchemaCompatibilityService schemaCompatibilityService,
                                        RenovateProperties properties) {
        this(contractRepository, repositoryFactory, schemaService, schemaCompatibilityService, properties,
                Clock.systemUTC());
    }

    RenovateCompatibilityService(MessageContractRepository contractRepository,
                                 MessageTypeRepositoryFactory repositoryFactory,
                                 MessageSchemaService schemaService,
                                 SchemaCompatibilityService schemaCompatibilityService,
                                 RenovateProperties properties,
                                 Clock clock) {
        this.contractRepository = contractRepository;
        this.repositoryFactory = repositoryFactory;
        this.schemaService = schemaService;
        this.schemaCompatibilityService = schemaCompatibilityService;
        this.deployedContractsCache = new ExpiringCache<>(properties.getCacheTtl(), clock);
        this.contractSchemasCache = new ExpiringCache<>(properties.getCacheTtl(), clock);
        this.snapshotCache = new ExpiringCache<>(properties.getCacheTtl(), clock);
        this.candidateSchemaCache = new ExpiringCache<>(properties.getCacheTtl(), clock);
    }

    public List<RenovateRelease> findGloballyCompatibleReleases(String packageName, String currentValue,
                                                                 String environment) {
        return findCompatibleReleases(null, packageName, currentValue, environment);
    }

    public List<RenovateRelease> findCompatibleReleasesForApp(String appName, String packageName, String currentValue,
                                                              String environment) {
        return findCompatibleReleases(appName, packageName, currentValue, environment);
    }

    private List<RenovateRelease> findCompatibleReleases(String appName, String packageName, String currentValue,
                                                          String environment) {
        long startNanos = System.nanoTime();
        MavenCoordinate coordinate = MavenCoordinate.parse(packageName);
        SemanticVersion current = SemanticVersion.parse(currentValue);
        validateRequest(appName, environment);
        String normalizedEnvironment = environment.toUpperCase(Locale.ROOT);
        String normalizedMessageType = coordinate.normalizedMessageType();
        List<DeployedMessageContract> prodContracts = deployedContractsCache.get(
                new DeployedContractsKey(normalizedEnvironment, normalizedMessageType),
                () -> List.copyOf(contractRepository.findCurrentlyDeployedContracts(
                        normalizedEnvironment, normalizedMessageType)));
        if (prodContracts.isEmpty()) {
            return failClosed(appName, packageName, "prod-contracts-not-found");
        }

        List<DeployedMessageContract> requestingContracts = appName == null ? List.of() : prodContracts.stream()
                .filter(contract -> contract.appName().equals(appName))
                .toList();
        if (appName != null && requestingContracts.isEmpty()) {
            return failClosed(appName, packageName, "app-prod-contracts-not-found");
        }

        Optional<DeployedMessageContract> registryContract = registryContract(
                appName == null ? prodContracts : requestingContracts);
        if (registryContract.isEmpty()) {
            return failClosed(appName, packageName, "registry-source-missing-or-ambiguous");
        }

        MessageTypeRepository.MessageTypeSnapshot snapshot;
        try {
            snapshot = snapshotCache.get(SnapshotKey.of(registryContract.get(), coordinate.definingSystem()),
                    () -> loadSnapshot(registryContract.get(), coordinate.definingSystem()));
        } catch (MessageTypeRepoException ex) {
            if (ex.isInfrastructureFailure()) {
                throw registryUnavailable(appName, packageName, ex);
            }
            return failClosed(appName, packageName, "registry-content-invalid");
        } catch (RuntimeException ex) {
            throw registryUnavailable(appName, packageName, ex);
        }

        List<SemanticVersion> candidateVersions;
        try {
            candidateVersions = snapshot.versions().stream()
                    .map(SemanticVersion::parse)
                    .toList();
        } catch (IllegalArgumentException ex) {
            return failClosed(appName, packageName, "invalid-registry-version");
        }
        List<SemanticVersion> newerVersions = candidateVersions.stream()
                .filter(version -> version.compareTo(current) > 0)
                .sorted(Comparator.naturalOrder())
                .toList();
        // Only load the schemas of the deployed contracts when there is a candidate to compare them with
        List<MessageContract> comparedContracts = newerVersions.isEmpty()
                ? List.of()
                : comparedContractsWithSchemas(prodContracts, requestingContracts);
        List<RenovateRelease> releases = newerVersions.stream()
                .filter(version -> candidateIsCompatible(registryContract.get(), snapshot.commitHash(), version.value(),
                        comparedContracts, requestingContracts))
                .map(version -> new RenovateRelease(version.value()))
                .toList();
        log.info("Renovate compatibility mode={} app={} package={} current={} environment={} prodContracts={} " +
                        "candidates={} compatible={} elapsedMs={}", appName == null ? "global" : "app-specific", appName,
                packageName, currentValue, normalizedEnvironment, prodContracts.size(), snapshot.versions().size(), releases.size(),
                (System.nanoTime() - startNanos) / 1_000_000);
        return releases;
    }

    private MessageTypeRepository.MessageTypeSnapshot loadSnapshot(DeployedMessageContract registryContract,
                                                                   String definingSystem) {
        try (MessageTypeRepository typeRepository = repositoryFactory.cloneRepository(registryContract.registryUrl())) {
            return typeRepository.getMessageTypeSnapshot(
                    registryContract.branch(), registryContract.messageType(), definingSystem);
        }
    }

    /**
     * @return the deployed contracts a candidate is compared with, in the order of the deployed contracts, with their
     * schemas: all deployed contracts in global mode, the same-topic opposite-role counterparts of the requesting
     * contracts in app-specific mode.
     */
    private List<MessageContract> comparedContractsWithSchemas(List<DeployedMessageContract> prodContracts,
                                                               List<DeployedMessageContract> requestingContracts) {
        List<DeployedMessageContract> compared = requestingContracts.isEmpty() ? prodContracts : prodContracts.stream()
                .filter(contract -> requestingContracts.stream().anyMatch(requesting ->
                        contract.topic().equals(requesting.topic()) && contract.role() == requesting.role().opposite()))
                .toList();
        if (compared.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = compared.stream().map(DeployedMessageContract::id).collect(Collectors.toUnmodifiableSet());
        Map<UUID, MessageContractSchema> schemas = contractSchemasCache.get(ids, () ->
                contractRepository.findSchemas(ids).stream()
                        .collect(Collectors.toUnmodifiableMap(MessageContractSchema::id, Function.identity())));
        return compared.stream()
                .map(contract -> withSchema(contract, schemas.get(contract.id())))
                .toList();
    }

    private static MessageContract withSchema(DeployedMessageContract contract, MessageContractSchema schema) {
        return MessageContract.builder()
                .appName(contract.appName())
                .appVersion(contract.appVersion())
                .messageType(contract.messageType())
                .messageTypeVersion(contract.messageTypeVersion())
                .topic(contract.topic())
                .role(contract.role())
                .registryUrl(contract.registryUrl())
                .branch(contract.branch())
                .compatibilityMode(contract.compatibilityMode())
                .encryptionKeyId(contract.encryptionKeyId())
                // A contract deleted since it was looked up has no schema and is therefore treated as incompatible
                .avroProtocolSchema(schema == null ? null : schema.avroProtocolSchema())
                .avroSchema(schema == null ? null : schema.avroSchema())
                .build();
    }

    private static void validateRequest(String appName, String environment) {
        if (environment == null || environment.isBlank()) {
            throw new IllegalArgumentException("Environment must not be blank");
        }
        if (appName != null && appName.isBlank()) {
            throw new IllegalArgumentException("Application name must not be blank when supplied");
        }
    }

    private static Optional<DeployedMessageContract> registryContract(List<DeployedMessageContract> contracts) {
        if (contracts.isEmpty()) {
            return Optional.empty();
        }
        DeployedMessageContract source = contracts.getFirst();
        if (source.registryUrl() == null || source.registryUrl().isBlank()
                || source.branch() == null || source.branch().isBlank()) {
            return Optional.empty();
        }
        boolean unambiguous = contracts.stream().allMatch(contract ->
                source.messageType().equals(contract.messageType())
                        && source.registryUrl().equals(contract.registryUrl())
                        && source.branch().equals(contract.branch()));
        return unambiguous ? Optional.of(source) : Optional.empty();
    }

    private boolean candidateIsCompatible(DeployedMessageContract registryContract, String snapshotCommitHash,
                                          String candidateVersion, List<MessageContract> comparedContracts,
                                          List<DeployedMessageContract> requestingContracts) {
        try {
            MessageContract candidate = candidateContract(registryContract, snapshotCommitHash, candidateVersion);
            loadCandidateSchema(candidate);
            if (requestingContracts.isEmpty()) {
                return globallyCompatible(candidate, comparedContracts);
            }
            return compatibleForApp(candidate, requestingContracts, comparedContracts);
        } catch (MessageTypeRepoException ex) {
            if (ex.isInfrastructureFailure()) {
                throw registryUnavailable(registryContract.appName(), registryContract.messageType(), ex);
            }
            log.info("Rejecting Renovate candidate version={} reason=schema-or-validation-error", candidateVersion, ex);
            return false;
        } catch (RuntimeException ex) {
            log.info("Rejecting Renovate candidate version={} reason=schema-or-validation-error", candidateVersion, ex);
            return false;
        }
    }

    /**
     * The candidate schema is read at the immutable snapshot commit, so the same candidate always has the same schema.
     */
    private void loadCandidateSchema(MessageContract candidate) {
        String schema = candidateSchemaCache.get(CandidateSchemaKey.of(candidate), () -> {
            schemaService.loadSchemas(List.of(candidate));
            return candidate.getAvroProtocolSchema();
        });
        candidate.setAvroProtocolSchema(schema);
    }

    private boolean globallyCompatible(MessageContract candidate, List<MessageContract> prodContracts) {
        return prodContracts.stream().allMatch(prodContract -> prodContract.getRole() == MessageContractRole.PRODUCER
                ? compatible(candidate, prodContract)
                : compatible(prodContract, candidate));
    }

    private boolean compatibleForApp(MessageContract candidate, List<DeployedMessageContract> requestingContracts,
                                     List<MessageContract> comparedContracts) {
        return requestingContracts.stream().allMatch(requestingContract -> {
            List<MessageContract> counterparts = comparedContracts.stream()
                    .filter(contract -> contract.getTopic().equals(requestingContract.topic()))
                    .filter(contract -> contract.getRole() == requestingContract.role().opposite())
                    .toList();
            if (counterparts.isEmpty()) {
                return false;
            }
            return counterparts.stream().allMatch(counterpart -> requestingContract.role() == MessageContractRole.CONSUMER
                    ? compatible(candidate, counterpart)
                    : compatible(counterpart, candidate));
        });
    }

    private boolean compatible(MessageContract reader, MessageContract writer) {
        return schemaCompatibilityService.validateReaderWriterCompatibility(reader, writer).isEmpty();
    }

    private static MessageContract candidateContract(DeployedMessageContract registryContract, String snapshotCommitHash,
                                                     String version) {
        return MessageContract.builder()
                .appName(registryContract.appName())
                .appVersion(registryContract.appVersion())
                .messageType(registryContract.messageType())
                .messageTypeVersion(version)
                .topic(registryContract.topic())
                .role(registryContract.role())
                .registryUrl(registryContract.registryUrl())
                .branch(registryContract.branch())
                .commitHash(snapshotCommitHash)
                .compatibilityMode(registryContract.compatibilityMode())
                .encryptionKeyId(registryContract.encryptionKeyId())
                .build();
    }

    private List<RenovateRelease> failClosed(String appName, String packageName, String reason) {
        log.info("Renovate compatibility app={} package={} compatible=0 reason={}", appName, packageName, reason);
        return List.of();
    }

    private RenovateRegistryException registryUnavailable(String appName, String packageName, RuntimeException cause) {
        log.error("Renovate registry unavailable app={} package={}", appName, packageName, cause);
        return new RenovateRegistryException("Message type registry is temporarily unavailable", cause);
    }

    private record DeployedContractsKey(String environment, String normalizedMessageType) {
    }

    private record SnapshotKey(String registryUrl, String branch, String messageType, String definingSystem) {
        private static SnapshotKey of(DeployedMessageContract registryContract, String definingSystem) {
            return new SnapshotKey(registryContract.registryUrl(), registryContract.branch(),
                    registryContract.messageType(), definingSystem);
        }
    }

    private record CandidateSchemaKey(String registryUrl, String branch, String commitHash, String messageType,
                                      String messageTypeVersion) {
        private static CandidateSchemaKey of(MessageContract candidate) {
            return new CandidateSchemaKey(candidate.getRegistryUrl(), candidate.getBranch(), candidate.getCommitHash(),
                    candidate.getMessageType(), candidate.getMessageTypeVersion());
        }
    }

    private record MavenCoordinate(String artifactId, String definingSystem) {

        private static final String MESSAGE_TYPE_GROUP_MARKER = ".messagetype.";

        private static MavenCoordinate parse(String packageName) {
            if (packageName == null) {
                throw new IllegalArgumentException("Expected a message-type Maven coordinate groupId:artifactId");
            }
            String[] parts = packageName.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Expected a message-type Maven coordinate groupId:artifactId");
            }
            int markerIndex = parts[0].indexOf(MESSAGE_TYPE_GROUP_MARKER);
            String definingSystem = markerIndex < 0
                    ? ""
                    : parts[0].substring(markerIndex + MESSAGE_TYPE_GROUP_MARKER.length());
            String groupPrefix = markerIndex < 0 ? "" : parts[0].substring(0, markerIndex);
            if (markerIndex <= 0 || !isSegmentedIdentifier(groupPrefix)
                    || !isIdentifier(definingSystem) || !isSegmentedIdentifier(parts[1])) {
                throw new IllegalArgumentException("Expected a message-type Maven coordinate groupId:artifactId");
            }
            return new MavenCoordinate(parts[1], definingSystem);
        }

        private static boolean isSegmentedIdentifier(String value) {
            boolean separator = true;
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (isIdentifierCharacter(character)) {
                    separator = false;
                } else if ((character == '.' || character == '-') && !separator) {
                    separator = true;
                } else {
                    return false;
                }
            }
            return !separator;
        }

        private static boolean isIdentifier(String value) {
            return !value.isEmpty() && value.chars().allMatch(character -> isIdentifierCharacter((char) character));
        }

        private static boolean isIdentifierCharacter(char character) {
            return character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_';
        }

        private String normalizedMessageType() {
            return normalize(artifactId);
        }

        private static String normalize(String value) {
            return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        }
    }

}
