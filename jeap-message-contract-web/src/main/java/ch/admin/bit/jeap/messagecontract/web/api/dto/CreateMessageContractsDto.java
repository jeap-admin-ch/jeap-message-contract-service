package ch.admin.bit.jeap.messagecontract.web.api.dto;

import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateMessageContractsDto(@Valid @NotNull List<NewMessageContractDto> contracts) {

    public List<MessageContract> toDomainObjects(String appName, String appVersion, String transactionId) {
        return contracts.stream()
                .map(contract -> contract.toNewDomainObject(appName, appVersion, transactionId))
                .toList();
    }
}
