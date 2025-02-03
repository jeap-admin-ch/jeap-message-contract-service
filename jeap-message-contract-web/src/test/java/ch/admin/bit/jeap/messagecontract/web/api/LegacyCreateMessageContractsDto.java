package ch.admin.bit.jeap.messagecontract.web.api;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record LegacyCreateMessageContractsDto(@Valid @NotNull List<LegacyNewMessageContractDto> contracts) {
}
