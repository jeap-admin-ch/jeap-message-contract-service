package ch.admin.bit.jeap.messagecontract.persistence;

import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MessageContractRepository {

    private final JpaMessageContractRepository jpaRepository;

    public void saveContracts(List<MessageContract> messageContracts) {
        jpaRepository.saveAll(messageContracts);
    }

    public void deleteContractById(UUID messageContractId    ) {
        jpaRepository.deleteContractById(messageContractId);
    }

    public int deleteContract(String appName, String appVersion, String messageType, String messageTypeVersion, String topic, MessageContractRole role) {
        return jpaRepository.deleteContract(ZonedDateTime.now(), appName, appVersion, messageType, messageTypeVersion, topic, role);
    }

    public int deleteContractsForAppVersion(String appName, String appVersion) {
        return jpaRepository.deleteByAppNameAndAppVersion(appName, appVersion);
    }

    public int deleteByAppNameAndAppVersionNotSameTransactionId(String appName, String appVersion, String transactionId) {
        return jpaRepository.deleteByAppNameAndAppVersionNotSameTransactionId(appName, appVersion, transactionId);
    }

    public boolean existsByAppNameAndAppVersion(String appName, String appVersion) {
        return jpaRepository.existsByAppNameAndAppVersionAndDeletedFalse(appName, appVersion);
    }

    public List<MessageContract> getContractsForAppVersionTransactionId(String appName, String appVersion, String transactionId) {
        return jpaRepository.findAllByAppNameAndAppVersionAndTransactionIdAndDeletedFalse(appName, appVersion, transactionId);
    }

    public List<MessageContract> getContractsForAppVersion(String appName, String appVersion) {
        return jpaRepository.findAllByAppNameAndAppVersionAndDeletedFalse(appName, appVersion);
    }

    public Set<String> distinctAppNameByRoleForMessageTypeOnTopic(String messageTypeName, String topic, MessageContractRole role) {
        return jpaRepository.distinctAppNameByRoleForMessageTypeOnTopic(messageTypeName, topic, role);
    }

    public List<MessageContract> getContractsForAppVersionAndMessageTypeOnTopicWithRole(String appName,
                                                                                        String appVersion,
                                                                                        String messageType,
                                                                                        String topic,
                                                                                        MessageContractRole role) {
        return jpaRepository.findAllByAppNameAndAppVersionAndMessageTypeAndTopicAndRoleAndDeletedFalse(
                appName, appVersion, messageType, topic, role);
    }

    public List<MessageContractInfo> findMessageContractInfosByEnvironment(String environment) {
        return jpaRepository.findAllByEnvironment(environment);
    }

    public List<MessageContractInfo> findAllMessageContractInfos() {
        return jpaRepository.findAllByDeletedFalse();
    }
}
