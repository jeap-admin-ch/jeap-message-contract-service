package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.domain.MessageContractService;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractInfo;
import ch.admin.bit.jeap.messagecontract.web.api.dto.CreateMessageContractsDto;
import ch.admin.bit.jeap.messagecontract.web.api.dto.MessageContractDto;
import ch.admin.bit.jeap.messagecontract.web.api.dto.MessageContractRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ContractController {

    private final MessageContractService messageContractService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List all contracts")
    public List<MessageContractDto> getContracts(@RequestParam(name = "env", required = false) String environment) {
        List<MessageContractInfo> contracts;
        if (StringUtils.isBlank(environment)) {
            contracts = new ArrayList<>(messageContractService.getAllContracts());
        } else {
            contracts = new ArrayList<>(messageContractService.getContractsForEnvironment(environment));
        }

        return contracts.stream()
                .map(MessageContractDto::fromDomainObject)
                .toList();
    }

    @PutMapping(path = "/{appName}/{appVersion}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Upload contracts for an app version. Users with role messagecontract-contract-upload are allowed to upload contracts")
    @PreAuthorize("hasAnyRole('messagecontract-write', 'messagecontract-contract-upload')")
    @ResponseStatus(HttpStatus.CREATED)
    public void uploadContracts(@Valid @RequestBody CreateMessageContractsDto messageContractsDto,
                                @NotBlank @PathVariable @Parameter(description = "Name of the application") String appName,
                                @NotBlank @PathVariable @Parameter(description = "Version of the application") String appVersion,
                                @RequestParam(required = false) @Parameter(description = "Identifies the upload transaction uniquely") String transactionId) {
        log.info("Contracts uploaded: app={}:{} transactionId={} contracts={} from:{}", appName, appVersion, transactionId, messageContractsDto,
                kv("user", SecurityContextHolder.getContext().getAuthentication().getName()));
        messageContractService.saveContracts(appName, appVersion, transactionId, messageContractsDto.toDomainObjects(appName, appVersion, transactionId));
    }

    @DeleteMapping("/{appName}/{appVersion}")
    @Operation(summary = "Delete a contract")
    @PreAuthorize("hasRole('messagecontract-write')")
    public void deleteContract(@NotBlank @PathVariable String appName,
                               @NotBlank @PathVariable String appVersion,
                               @NotBlank @RequestParam String messageType,
                               @NotBlank @RequestParam String messageTypeVersion,
                               @NotBlank @RequestParam String topic,
                               @NotNull @RequestParam MessageContractRole role) {
        int deleteContractCount = messageContractService.deleteContract(appName, appVersion, messageType, messageTypeVersion, topic, role.toDomainObject());
        log.info("Marked {} contract(s) as deleted: {} {} {} {} {} {}",
                deleteContractCount, appName, appVersion, messageType, messageTypeVersion, topic, role);
    }
}
