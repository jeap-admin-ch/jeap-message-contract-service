package ch.admin.bit.jeap.messagecontract.domain.renovate;

import ch.admin.bit.jeap.messagecontract.domain.compatibility.SchemaCompatibilityService;
import ch.admin.bit.jeap.messagecontract.domain.schema.MessageSchemaService;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateCompatibilityServiceCacheTest.PACKAGE_NAME;
import static ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateCompatibilityServiceCacheTest.contract;
import static ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateCompatibilityServiceCacheTest.givenDeployedContractsAndRegistry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies with the real cache manager and the Spring caching proxy that a zero time to live disables the reuse.
 */
@SpringJUnitConfig(classes = {RenovateCompatibilityServiceCacheTest.TestConfig.class, RenovateCacheConfiguration.class,
        RenovateLookups.class, RenovateCompatibilityService.class})
@TestPropertySource(properties = "jeap.messagecontract.renovate.cache-ttl=0s")
class RenovateCompatibilityServiceZeroTtlTest {

    @Autowired
    private RenovateCompatibilityService service;
    @MockitoBean
    private MessageContractRepository contractRepository;
    @MockitoBean
    private MessageTypeRepositoryFactory repositoryFactory;
    @MockitoBean
    private MessageSchemaService schemaService;
    @MockitoBean
    private SchemaCompatibilityService compatibilityService;

    @Test
    void cachingIsDisabledWithZeroTtl() {
        givenDeployedContractsAndRegistry(contractRepository, repositoryFactory,
                contract("producer", MessageContractRole.PRODUCER));
        when(compatibilityService.validateReaderWriterCompatibility(any(), any())).thenReturn(List.of());

        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");
        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");

        verify(contractRepository, times(2)).findCurrentlyDeployedContracts("PROD", "activzoneenteredevent");
        verify(repositoryFactory, times(2)).cloneRepository("registry-url");
        verify(schemaService, times(2)).loadSchemas(anyList());
    }
}
