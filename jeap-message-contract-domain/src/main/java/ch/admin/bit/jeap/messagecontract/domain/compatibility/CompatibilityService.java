package ch.admin.bit.jeap.messagecontract.domain.compatibility;

import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.ConsumerProducerInteraction;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.Incompatibility;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.InteractionRole;
import ch.admin.bit.jeap.messagecontract.persistence.DeploymentRepository;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CompatibilityService {

    private final MessageContractRepository messageContractRepository;
    private final SchemaCompatibilityService schemaCompatibilityService;
    private final DeploymentRepository deploymentRepository;

    /**
     * For each consumer/producer counterpart of all contracts of the given app version, checks if the schema is compatible
     * with the counterpart. Only interactions using the same topic are taken into account. For each counterpart,
     * the contracts of the version currently deployed on the given environment are used. Defaults to compatibility,
     * i.e. if no contracts are found or if no known counterparts are deployed on the environment, no incompatibilities
     * are assumed.
     */
    @Transactional(readOnly = true)
    @Timed(value = "checkcompatibility.time", description = "Time taken for the compatibility check", histogram = true)
    public CompatibilityCheckResult checkCompatibility(String appName, String appVersion, String environment) {
        List<MessageContract> contracts = messageContractRepository.getContractsForAppVersion(appName, appVersion);
        return checkCompatibilityForContracts(environment, contracts);
    }

    private CompatibilityCheckResult checkCompatibilityForContracts(String environment, List<MessageContract> contracts) {
        List<ConsumerProducerInteraction> interactions = new ArrayList<>();
        List<Incompatibility> incompatibilities = new ArrayList<>();
        for (MessageContract contract : contracts) {
            checkCompatibilityForContract(environment, interactions, incompatibilities, contract);
        }

        return new CompatibilityCheckResult(interactions, incompatibilities);
    }

    private void checkCompatibilityForContract(String environment,
                                               List<ConsumerProducerInteraction> interactions,
                                               List<Incompatibility> incompatibilities,
                                               MessageContract contract) {
        // Per contract, get the target counterparts (consumer->producers, producer->consumers)
        Set<String> appsInteractedWith = getAppNamesInteractedWith(contract);

        for (String appInteractedWith : appsInteractedWith) {
            Optional<String> deployedAppVersionOptional = deploymentRepository.findAppVersionCurrentlyDeployedOnEnvironment(appInteractedWith, environment);
            deployedAppVersionOptional.ifPresent(deployedVersion ->
                    checkCompatibilityWithApp(interactions, incompatibilities, contract, appInteractedWith, deployedVersion));
        }
    }

    private void checkCompatibilityWithApp(List<ConsumerProducerInteraction> interactions, List<Incompatibility> incompatibilities, MessageContract contract, String appInteractedWith, String deployedVersion) {
        List<MessageContract> interactedWithContracts = messageContractRepository
                .getContractsForAppVersionAndMessageTypeOnTopicWithRole(appInteractedWith, deployedVersion,
                        contract.getMessageType(), contract.getTopic(), contract.getRole().opposite());

        // If any matches are found, validate compatibility
        for (MessageContract interactedWithContract : interactedWithContracts) {
            ConsumerProducerInteraction interaction = createInteraction(appInteractedWith, deployedVersion, interactedWithContract);
            interactions.add(interaction);
            validateCompatibility(incompatibilities, contract, interactedWithContract, interaction);
        }
    }

    private void validateCompatibility(List<Incompatibility> incompatibilities, MessageContract appContract,
                                       MessageContract interactedWithContract, ConsumerProducerInteraction interaction) {
        List<SchemaIncompatibility> schemaIncompatibilities =
                schemaCompatibilityService.validateCompatibility(appContract, interactedWithContract);
        if (!schemaIncompatibilities.isEmpty()) {
            incompatibilities.add(new Incompatibility(ConsumerProducerInteraction.from(appContract), interaction, schemaIncompatibilities));
        }
    }

    private Set<String> getAppNamesInteractedWith(MessageContract contract) {
        return messageContractRepository.distinctAppNameByRoleForMessageTypeOnTopic(
                contract.getMessageType(), contract.getTopic(), contract.getRole().opposite());
    }

    private static ConsumerProducerInteraction createInteraction(String appInteractedWith, String deployedVersion, MessageContract interactedWithContract) {
        return new ConsumerProducerInteraction(appInteractedWith, deployedVersion,
                interactedWithContract.getMessageType(), interactedWithContract.getMessageTypeVersion(),
                interactedWithContract.getTopic(), InteractionRole.from(interactedWithContract.getRole()));
    }
}
