package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractInfo;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
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
            log.warn("Skipping latest message version lookup for {} contract(s) without a registry URL", contracts.size());
            return List.of();
        }

        Map<String, List<String>> versionsByMessageType;
        try (MessageTypeRepository repository = repositoryFactory.cloneRepository(registryUrl)) {
            versionsByMessageType = repository.getMessageTypeVersionsFromDefaultBranch(
                    contracts.stream().map(MessageContractInfo::getMessageType).collect(Collectors.toSet()));
        } catch (RuntimeException ex) {
            log.warn("Skipping latest message version lookup for {} contract(s) from registry {}: {}",
                    contracts.size(), registryUrl, ex.getMessage(), ex);
            return List.of();
        }

        return contracts.stream()
                .map(contract -> toVersionStatusOrNull(contract, versionsByMessageType.get(contract.getMessageType())))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static MessageContractVersionStatus toVersionStatusOrNull(MessageContractInfo contract,
                                                                        List<String> versions) {
        try {
            return toVersionStatus(contract, versions);
        } catch (IllegalArgumentException ex) {
            log.warn("Skipping latest message version status for {}:{} message type {}: {}",
                    contract.getAppName(), contract.getAppVersion(), contract.getMessageType(), ex.getMessage());
            return null;
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
