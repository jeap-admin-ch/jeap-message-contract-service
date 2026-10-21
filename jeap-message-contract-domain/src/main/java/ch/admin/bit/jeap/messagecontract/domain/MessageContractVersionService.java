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
            // A contract without registry URL is a permanent data defect of that contract, not an outage. Rejecting
            // the whole snapshot would block the version status of all other applications until it is fixed.
            return contracts.stream()
                    .map(contract -> unknown(contract, "<none>", "the contract has no message type registry URL"))
                    .toList();
        }

        // A failure to read the registry as a whole is typically transient and affects every contract of the
        // registry. Returning UNKNOWN for all of them would replace the consumer's last good snapshot with a
        // misleading one, so the request is rejected and the consumer retains its previous data.
        Map<String, List<String>> versionsByMessageType;
        try (MessageTypeRepository repository = repositoryFactory.cloneRepository(registryUrl)) {
            versionsByMessageType = repository.getMessageTypeVersionsFromDefaultBranch(
                    contracts.stream().map(MessageContractInfo::getMessageType).collect(Collectors.toSet()));
        } catch (RuntimeException ex) {
            throw new MessageContractVersionStatusUnavailableException(
                    "Cannot resolve complete message version status from registry " + registryUrl, ex);
        }

        return contracts.stream()
                .map(contract -> toVersionStatusOrUnknown(contract, registryUrl,
                        versionsByMessageType.get(contract.getMessageType())))
                .toList();
    }

    private static MessageContractVersionStatus toVersionStatusOrUnknown(MessageContractInfo contract,
                                                                          String registryUrl,
                                                                          List<String> versions) {
        try {
            return toVersionStatus(contract, versions);
        } catch (IllegalArgumentException ex) {
            return unknown(contract, registryUrl, ex.getMessage());
        }
    }

    private static MessageContractVersionStatus unknown(MessageContractInfo contract, String registryUrl,
                                                        String reason) {
        log.warn("Latest message version of {}:{} message type {} is UNKNOWN (registry {}): {}",
                contract.getAppName(), contract.getAppVersion(), contract.getMessageType(), registryUrl, reason);
        return MessageContractVersionStatus.unknown(
                contract.getAppName(),
                contract.getAppVersion(),
                contract.getMessageType(),
                contract.getMessageTypeVersion(),
                contract.getTopic(),
                MessageContractRole.valueOf(contract.getRole()));
    }

    private static MessageContractVersionStatus toVersionStatus(MessageContractInfo contract, List<String> versions) {
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("No versions found for message type " + contract.getMessageType()
                    + " on the default branch of the registry");
        }
        String latestVersion = versions.stream()
                .map(SemanticVersion::parse)
                .max(Comparator.naturalOrder())
                .orElseThrow()
                .value();
        SemanticVersion.parse(contract.getMessageTypeVersion());

        return MessageContractVersionStatus.resolved(
                contract.getAppName(),
                contract.getAppVersion(),
                contract.getMessageType(),
                contract.getMessageTypeVersion(),
                latestVersion,
                contract.getTopic(),
                MessageContractRole.valueOf(contract.getRole()));
    }
}
