package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.domain.schema.MessageSchemaService;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractInfo;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import io.micrometer.core.annotation.Timed;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageContractService {

    private final MessageContractRepository messageContractRepository;
    private final MessageSchemaService messageSchemaService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<MessageContractInfo> getAllContracts() {
        return messageContractRepository.findAllMessageContractInfos();
    }

    @Transactional(readOnly = true)
    public List<MessageContractInfo> getContractsForEnvironment(String environment) {
        return messageContractRepository.findMessageContractInfosByEnvironment(environment.toUpperCase());
    }

    @Transactional
    @Timed(value = "savecontracts.time", description = "Time taken to save a contract including its schema", histogram = true)
    public void saveContracts(String appName, String appVersion, String transactionId, List<MessageContract> messageContracts) {
        if (transactionId == null) {
            saveContracts(appName, appVersion, messageContracts);
        } else {
            log.debug("Delete contracts for appName={} appVersion={} and not for transactionId={}", appName, appVersion, transactionId);
            int deletedContracts = messageContractRepository.deleteByAppNameAndAppVersionNotSameTransactionId(appName, appVersion, transactionId);
            entityManager.flush(); // flush delete before inserts
            log.debug("Deleted {} contracts before inserting {} new contracts", deletedContracts, messageContracts.size());

            removeDuplicateContracts(messageContractRepository.getContractsForAppVersionTransactionId(appName, appVersion, transactionId), messageContracts);

            messageSchemaService.loadSchemas(messageContracts);
            messageContractRepository.saveContracts(messageContracts);
        }

    }

    private void removeDuplicateContracts(List<MessageContract> contractsInDb, List<MessageContract> messageContracts) {
        if (!contractsInDb.isEmpty()) {
            for (MessageContract messageContract : messageContracts) {
                Optional<MessageContract> messageContractInDb = contractsInDb.stream().filter(
                                mc -> mc.getAppName().equals(messageContract.getAppName()) &&
                                        mc.getAppVersion().equals(messageContract.getAppVersion()) &&
                                        mc.getMessageType().equals(messageContract.getMessageType()) &&
                                        mc.getMessageTypeVersion().equals(messageContract.getMessageTypeVersion()) &&
                                        mc.getRole().equals(messageContract.getRole()) &&
                                        mc.getTopic().equals(messageContract.getTopic()))
                        .findFirst();

                messageContractInDb.ifPresent(
                        contract -> {
                            messageContractRepository.deleteContractById(contract.getId());
                            log.debug("Deleted duplicated contract with id {}", contract.getId());
                });
            }
        }
    }

    private void saveContracts(String appName, String appVersion, List<MessageContract> messageContracts) {
        int deletedContracts = messageContractRepository.deleteContractsForAppVersion(appName, appVersion);
        entityManager.flush(); // flush delete before inserts
        log.debug("Deleted {} contracts before inserting {} new contracts", deletedContracts, messageContracts.size());
        messageSchemaService.loadSchemas(messageContracts);
        messageContractRepository.saveContracts(messageContracts);
    }

    @Transactional
    public int deleteContract(String appName, String appVersion, String messageType, String messageTypeVersion,
                              String topic, MessageContractRole role) {
        return messageContractRepository.deleteContract(appName, appVersion, messageType, messageTypeVersion, topic, role);
    }
}
