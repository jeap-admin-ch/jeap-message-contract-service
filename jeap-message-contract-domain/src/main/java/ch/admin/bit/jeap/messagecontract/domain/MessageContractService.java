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

import static ch.admin.bit.jeap.messagecontract.messagetype.repository.Elapsed.elapsedMs;

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
        long startNanos = System.nanoTime();
        log.info("Saving {} contracts for {}:{} transactionId={}", messageContracts.size(), appName, appVersion, transactionId);
        if (transactionId == null) {
            saveContracts(appName, appVersion, messageContracts);
        } else {
            log.debug("Delete contracts for appName={} appVersion={} and not for transactionId={}", appName, appVersion, transactionId);
            long deleteStart = System.nanoTime();
            int deletedContracts = messageContractRepository.deleteByAppNameAndAppVersionNotSameTransactionId(appName, appVersion, transactionId);
            removeDuplicateContracts(messageContractRepository.getContractsForAppVersionTransactionId(appName, appVersion, transactionId), messageContracts);
            entityManager.flush(); // flush delete before inserts
            log.debug("Deleted {} contracts in {}ms before inserting {} new contracts", deletedContracts, elapsedMs(deleteStart), messageContracts.size());

            messageSchemaService.loadSchemas(messageContracts);

            long saveStart = System.nanoTime();
            messageContractRepository.saveContracts(messageContracts);
            log.debug("Persisted {} contracts in {} ms", messageContracts.size(), elapsedMs(saveStart));
        }
        log.info("Saved contracts for {}:{} transactionId={} in {}ms", appName, appVersion, transactionId, elapsedMs(startNanos));
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
        long deleteStart = System.nanoTime();
        int deletedContracts = messageContractRepository.deleteContractsForAppVersion(appName, appVersion);
        entityManager.flush(); // flush delete before inserts
        log.info("Deleted {} contracts (no-tx-id) in {} ms before inserting {}", deletedContracts, elapsedMs(deleteStart), messageContracts.size());

        messageSchemaService.loadSchemas(messageContracts);

        long saveStart = System.nanoTime();
        messageContractRepository.saveContracts(messageContracts);
        log.info("Persisted {} contracts (no-tx-id) in {} ms", messageContracts.size(), elapsedMs(saveStart));
    }

    @Transactional
    public int deleteContract(String appName, String appVersion, String messageType, String messageTypeVersion,
                              String topic, MessageContractRole role) {
        return messageContractRepository.deleteContract(appName, appVersion, messageType, messageTypeVersion, topic, role);
    }
}
