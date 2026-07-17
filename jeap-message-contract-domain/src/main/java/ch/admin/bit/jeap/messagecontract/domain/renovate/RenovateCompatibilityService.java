package ch.admin.bit.jeap.messagecontract.domain.renovate;

import ch.admin.bit.jeap.messagecontract.domain.compatibility.SchemaCompatibilityService;
import ch.admin.bit.jeap.messagecontract.domain.schema.MessageSchemaService;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepoException;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class RenovateCompatibilityService {

    private static final Pattern SEMANTIC_VERSION = Pattern.compile("(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)");
    private static final Pattern MAVEN_COORDINATE = Pattern.compile(
            "[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*\\.messagetype\\.[A-Za-z0-9_]+:[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*");

    private final MessageContractRepository contractRepository;
    private final MessageTypeRepositoryFactory repositoryFactory;
    private final MessageSchemaService schemaService;
    private final SchemaCompatibilityService schemaCompatibilityService;

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
        if (environment == null || environment.isBlank()) {
            throw new IllegalArgumentException("Environment must not be blank");
        }
        if (appName != null && appName.isBlank()) {
            throw new IllegalArgumentException("Application name must not be blank when supplied");
        }
        String normalizedEnvironment = environment.toUpperCase(Locale.ROOT);
        List<MessageContract> prodContracts = contractRepository.findCurrentlyDeployedContracts(
                normalizedEnvironment, coordinate.normalizedMessageType());
        if (prodContracts.isEmpty()) {
            return failClosed(appName, packageName, "prod-contracts-not-found");
        }

        List<MessageContract> requestingContracts = appName == null ? List.of() : prodContracts.stream()
                .filter(contract -> contract.getAppName().equals(appName))
                .toList();
        if (appName != null && requestingContracts.isEmpty()) {
            return failClosed(appName, packageName, "app-prod-contracts-not-found");
        }

        Optional<MessageContract> registryContract = registryContract(
                appName == null ? prodContracts : requestingContracts);
        if (registryContract.isEmpty()) {
            return failClosed(appName, packageName, "registry-source-missing-or-ambiguous");
        }

        MessageTypeRepository.MessageTypeSnapshot snapshot;
        try (MessageTypeRepository typeRepository = repositoryFactory.cloneRepository(registryContract.get().getRegistryUrl())) {
            snapshot = typeRepository.getMessageTypeSnapshot(
                    registryContract.get().getBranch(), registryContract.get().getMessageType(),
                    coordinate.definingSystem());
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
        List<RenovateRelease> releases = candidateVersions.stream()
                .filter(version -> version.compareTo(current) > 0)
                .sorted(Comparator.naturalOrder())
                .filter(version -> candidateIsCompatible(registryContract.get(), snapshot.commitHash(), version.value(), prodContracts,
                        requestingContracts))
                .map(version -> new RenovateRelease(version.value()))
                .toList();
        log.info("Renovate compatibility mode={} app={} package={} current={} environment={} prodContracts={} " +
                        "candidates={} compatible={} elapsedMs={}", appName == null ? "global" : "app-specific", appName,
                packageName, currentValue, normalizedEnvironment, prodContracts.size(), snapshot.versions().size(), releases.size(),
                (System.nanoTime() - startNanos) / 1_000_000);
        return releases;
    }

    private static Optional<MessageContract> registryContract(List<MessageContract> contracts) {
        if (contracts.isEmpty()) {
            return Optional.empty();
        }
        MessageContract source = contracts.getFirst();
        if (source.getRegistryUrl() == null || source.getRegistryUrl().isBlank()
                || source.getBranch() == null || source.getBranch().isBlank()) {
            return Optional.empty();
        }
        boolean unambiguous = contracts.stream().allMatch(contract ->
                source.getMessageType().equals(contract.getMessageType())
                        && source.getRegistryUrl().equals(contract.getRegistryUrl())
                        && source.getBranch().equals(contract.getBranch()));
        return unambiguous ? Optional.of(source) : Optional.empty();
    }

    private boolean candidateIsCompatible(MessageContract registryContract, String snapshotCommitHash, String candidateVersion,
                                           List<MessageContract> prodContracts,
                                           List<MessageContract> requestingContracts) {
        try {
            MessageContract candidate = candidateContract(registryContract, snapshotCommitHash, candidateVersion);
            schemaService.loadSchemas(List.of(candidate));
            if (requestingContracts.isEmpty()) {
                return globallyCompatible(candidate, prodContracts);
            }
            return compatibleForApp(candidate, requestingContracts, prodContracts);
        } catch (MessageTypeRepoException ex) {
            if (ex.isInfrastructureFailure()) {
                throw registryUnavailable(registryContract.getAppName(), registryContract.getMessageType(), ex);
            }
            log.info("Rejecting Renovate candidate version={} reason=schema-or-validation-error", candidateVersion, ex);
            return false;
        } catch (RuntimeException ex) {
            log.info("Rejecting Renovate candidate version={} reason=schema-or-validation-error", candidateVersion, ex);
            return false;
        }
    }

    private boolean globallyCompatible(MessageContract candidate, List<MessageContract> prodContracts) {
        return prodContracts.stream().allMatch(prodContract -> prodContract.getRole() == MessageContractRole.PRODUCER
                ? compatible(candidate, prodContract)
                : compatible(prodContract, candidate));
    }

    private boolean compatibleForApp(MessageContract candidate, List<MessageContract> requestingContracts,
                                     List<MessageContract> prodContracts) {
        return requestingContracts.stream().allMatch(requestingContract -> {
            List<MessageContract> counterparts = prodContracts.stream()
                    .filter(contract -> contract.getTopic().equals(requestingContract.getTopic()))
                    .filter(contract -> contract.getRole() == requestingContract.getRole().opposite())
                    .toList();
            if (counterparts.isEmpty()) {
                return false;
            }
            return counterparts.stream().allMatch(counterpart -> requestingContract.getRole() == MessageContractRole.CONSUMER
                    ? compatible(candidate, counterpart)
                    : compatible(counterpart, candidate));
        });
    }

    private boolean compatible(MessageContract reader, MessageContract writer) {
        return schemaCompatibilityService.validateReaderWriterCompatibility(reader, writer).isEmpty();
    }

    private static MessageContract candidateContract(MessageContract registryContract, String snapshotCommitHash, String version) {
        return MessageContract.builder()
                .appName(registryContract.getAppName())
                .appVersion(registryContract.getAppVersion())
                .messageType(registryContract.getMessageType())
                .messageTypeVersion(version)
                .topic(registryContract.getTopic())
                .role(registryContract.getRole())
                .registryUrl(registryContract.getRegistryUrl())
                .branch(registryContract.getBranch())
                .commitHash(snapshotCommitHash)
                .compatibilityMode(registryContract.getCompatibilityMode())
                .encryptionKeyId(registryContract.getEncryptionKeyId())
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

    private record MavenCoordinate(String artifactId, String definingSystem) {

        private static final String MESSAGE_TYPE_GROUP_MARKER = ".messagetype.";

        private static MavenCoordinate parse(String packageName) {
            if (packageName == null || !MAVEN_COORDINATE.matcher(packageName).matches()) {
                throw new IllegalArgumentException("Expected a message-type Maven coordinate groupId:artifactId");
            }
            String[] parts = packageName.split(":", -1);
            int markerIndex = parts[0].indexOf(MESSAGE_TYPE_GROUP_MARKER);
            String definingSystem = markerIndex < 0
                    ? ""
                    : parts[0].substring(markerIndex + MESSAGE_TYPE_GROUP_MARKER.length());
            if (markerIndex <= 0 || definingSystem.isBlank() || definingSystem.contains(".")) {
                throw new IllegalArgumentException("Expected a message-type Maven coordinate groupId:artifactId");
            }
            return new MavenCoordinate(parts[1], definingSystem);
        }

        private String normalizedMessageType() {
            return normalize(artifactId);
        }

        private static String normalize(String value) {
            return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        }
    }

    private record SemanticVersion(String value, int major, int minor, int patch) implements Comparable<SemanticVersion> {
        private static SemanticVersion parse(String value) {
            if (value == null || !SEMANTIC_VERSION.matcher(value).matches()) {
                throw new IllegalArgumentException("Expected semantic version x.y.z: " + value);
            }
            String[] parts = value.split("\\.", -1);
            try {
                return new SemanticVersion(value, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Expected semantic version x.y.z: " + value, ex);
            }
        }

        @Override
        public int compareTo(SemanticVersion other) {
            return Comparator.comparingInt(SemanticVersion::major)
                    .thenComparingInt(SemanticVersion::minor)
                    .thenComparingInt(SemanticVersion::patch)
                    .compare(this, other);
        }
    }
}
