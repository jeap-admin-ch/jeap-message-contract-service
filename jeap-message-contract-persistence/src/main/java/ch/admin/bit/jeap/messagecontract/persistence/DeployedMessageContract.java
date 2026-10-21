package ch.admin.bit.jeap.messagecontract.persistence;

import ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;

import java.util.UUID;

/**
 * A currently deployed message contract without its avro schemas. The schemas are large and only needed for the
 * contracts that are actually compared, see {@link MessageContractSchema}.
 */
public record DeployedMessageContract(UUID id,
                                      String appName,
                                      String appVersion,
                                      String messageType,
                                      String messageTypeVersion,
                                      String topic,
                                      MessageContractRole role,
                                      String registryUrl,
                                      String branch,
                                      CompatibilityMode compatibilityMode,
                                      String encryptionKeyId) {
}
