package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MessageContractTestFactory {

    public static MessageContract createContract(String appName, String appVersion, String messageType, String encryptionKeyId) {
        return MessageContract.builder()
                .appName(appName)
                .appVersion(appVersion)
                .messageType(messageType)
                .messageTypeVersion("1.0.0")
                .topic("topic")
                .role(encryptionKeyId == null ? MessageContractRole.CONSUMER : MessageContractRole.PRODUCER)
                .registryUrl("https://git/repo")
                .commitHash("1234")
                .branch("main")
                .compatibilityMode(CompatibilityMode.BACKWARD)
                .avroProtocolSchema("{}")
                .build();
    }

}
