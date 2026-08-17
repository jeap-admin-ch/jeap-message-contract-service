package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepoException;
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

        Map<RegistryReference, List<MessageContractInfo>> contractsByRegistry = contractRepository
                .findMessageContractInfosByEnvironment(environment.toUpperCase(Locale.ROOT)).stream()
                .collect(Collectors.groupingBy(contract -> new RegistryReference(
                        contract.getRegistryUrl(), contract.getBranch())));

        List<MessageContractVersionStatus> statuses = new ArrayList<>();
        contractsByRegistry.forEach((registry, contracts) -> statuses.addAll(getVersionStatus(registry, contracts)));
        return statuses.stream()
                .sorted(Comparator.comparing(MessageContractVersionStatus::appName)
                        .thenComparing(MessageContractVersionStatus::messageType)
                        .thenComparing(status -> status.role().name())
                        .thenComparing(MessageContractVersionStatus::topic))
                .toList();
    }

    private List<MessageContractVersionStatus> getVersionStatus(RegistryReference registry,
                                                                 List<MessageContractInfo> contracts) {
        if (registry.url() == null || registry.url().isBlank()) {
            throw new IllegalArgumentException("Message contract registry URL must not be blank");
        }

        Map<String, List<String>> versionsByMessageType;
        try (MessageTypeRepository repository = repositoryFactory.cloneRepository(registry.url())) {
            versionsByMessageType = repository.getMessageTypeVersions(
                    registry.branch(),
                    contracts.stream().map(MessageContractInfo::getMessageType).collect(Collectors.toSet()));
        } catch (MessageTypeRepoException ex) {
            if (ex.isInfrastructureFailure()) {
                throw new MessageContractRegistryException("Message type registry is temporarily unavailable", ex);
            }
            throw ex;
        } catch (RuntimeException ex) {
            throw new MessageContractRegistryException("Message type registry is temporarily unavailable", ex);
        }

        return contracts.stream()
                .map(contract -> toVersionStatus(contract, versionsByMessageType.get(contract.getMessageType())))
                .toList();
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

    private record RegistryReference(String url, String branch) {
    }

}
