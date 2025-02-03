package ch.admin.bit.jeap.messagecontract.domain.compatibility;

import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;

import java.util.List;

public record CompatibilityCheckResult(boolean compatible,
                                       List<ConsumerProducerInteraction> interactions,
                                       List<Incompatibility> incompatibilities) {

    CompatibilityCheckResult(List<ConsumerProducerInteraction> interactions, List<Incompatibility> incompatibilities) {
        this(incompatibilities.isEmpty(), interactions, incompatibilities);
    }

    public record ConsumerProducerInteraction(String appName,
                                              String appVersion,
                                              String messageType,
                                              String messageTypeVersion,
                                              String topic,
                                              InteractionRole role) {

        public static ConsumerProducerInteraction from(MessageContract appContract) {
            return new ConsumerProducerInteraction(
                    appContract.getAppName(),
                    appContract.getAppVersion(),
                    appContract.getMessageType(),
                    appContract.getMessageTypeVersion(),
                    appContract.getTopic(),
                    InteractionRole.from(appContract.getRole()));
        }
    }

    public String getMessage() {
        return IncompatibilityMessageBuilder.build(incompatibilities);
    }

    public enum InteractionRole {
        PRODUCER("producing"), CONSUMER("consuming");

        private final String verb;

        InteractionRole(String verb) {
            this.verb = verb;
        }

        public static InteractionRole from(MessageContractRole role) {
            return role == MessageContractRole.CONSUMER ? CONSUMER : PRODUCER;
        }

        public String verb() {
            return verb;
        }
    }

    public record Incompatibility(ConsumerProducerInteraction source,
                                  ConsumerProducerInteraction target,
                                  List<SchemaIncompatibility> schemaIncompatibilities) {
    }
}
