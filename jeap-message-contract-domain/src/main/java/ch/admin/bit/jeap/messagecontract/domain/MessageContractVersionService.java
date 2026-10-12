package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractInfo;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MessageContractVersionService {

    private final MessageContractRepository contractRepository;
    private final MessageTypeRepositoryFactory repositoryFactory;

    public List<MessageContractVersionStatus> getVersionStatus(String environment) {
        if (environment == null || environment.isBlank()) {
            throw new IllegalArgumentException("Environment must not be blank");
        }

        Map<String, List<MessageContractInfo>> contractsByRegistry = contractRepository
                .findMessageContractInfosByEnvironment(environment.toUpperCase(Locale.ROOT)).stream()
                .collect(Collectors.groupingBy(contract -> contract.getRegistryUrl() == null ? "" : contract.getRegistryUrl()));

        List<MessageContractVersionStatus> statuses = new ArrayList<>();
        contractsByRegistry.forEach((registryUrl, contracts) -> statuses.addAll(getVersionStatus(registryUrl, contracts)));
        return statuses.stream()
                .sorted(Comparator.comparing(MessageContractVersionStatus::appName)
                        .thenComparing(MessageContractVersionStatus::messageType)
                        .thenComparing(status -> status.role().name())
                        .thenComparing(MessageContractVersionStatus::topic))
                .toList();
    }

    private List<MessageContractVersionStatus> getVersionStatus(String registryUrl,
                                                                 List<MessageContractInfo> contracts) {
        if (registryUrl.isBlank()) {
            throw new MessageContractVersionStatusUnavailableException(
                    "Cannot resolve complete message version status: deployed contracts have no registry URL");
        }

        Map<String, List<String>> versionsByMessageType;
        try (MessageTypeRepository repository = repositoryFactory.cloneRepository(registryUrl)) {
            versionsByMessageType = repository.getMessageTypeVersionsFromDefaultBranch(
                    contracts.stream().map(MessageContractInfo::getMessageType).collect(Collectors.toSet()));
        } catch (RuntimeException ex) {
            throw new MessageContractVersionStatusUnavailableException(
                    "Cannot resolve complete message version status from registry " + registryUrl, ex);
        }

        return contracts.stream()
                .map(contract -> toVersionStatusOrThrow(contract, versionsByMessageType.get(contract.getMessageType())))
                .toList();
    }

    private static MessageContractVersionStatus toVersionStatusOrThrow(MessageContractInfo contract,
                                                                        List<String> versions) {
        try {
            return toVersionStatus(contract, versions);
        } catch (IllegalArgumentException ex) {
            throw new MessageContractVersionStatusUnavailableException(
                    "Cannot resolve complete message version status for %s:%s message type %s"
                            .formatted(contract.getAppName(), contract.getAppVersion(), contract.getMessageType()), ex);
        }
    }

    private static MessageContractVersionStatus toVersionStatus(MessageContractInfo contract, List<String> versions) {
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("No versions found for message type " + contract.getMessageType());
        }
        String latestVersion = versions.stream()
                .map(SemanticVersion::parse)
                .max(Comparator.naturalOrder())
                .orElseThrow()
                .value();
        SemanticVersion.parse(contract.getMessageTypeVersion());

        return new MessageContractVersionStatus(
                contract.getAppName(),
                contract.getAppVersion(),
                contract.getMessageType(),
                contract.getMessageTypeVersion(),
                latestVersion,
                contract.getTopic(),
                MessageContractRole.valueOf(contract.getRole()));
    }
}
