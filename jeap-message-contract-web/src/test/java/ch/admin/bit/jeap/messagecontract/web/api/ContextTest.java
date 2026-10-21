package ch.admin.bit.jeap.messagecontract.web.api;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;

@SpringBootTest
@Slf4j
class ContextTest extends PostgresTestContainerBase {

    private final ContractController contractController;
    private final ApplicationContext applicationContext;

    @Autowired
    ContextTest(ContractController contractController, ApplicationContext applicationContext) {
        this.contractController = contractController;
        this.applicationContext = applicationContext;
    }

    @Test
    void contextLoads() {
        assertNotNull(contractController);
    }

    @Test
    void renovateCacheManagerDoesNotReplaceTheApplicationCacheManager() {
        CacheManager renovateCacheManager = applicationContext.getBean("renovateCacheManager", CacheManager.class);

        assertThat(applicationContext.getBean(CacheManager.class)).isNotSameAs(renovateCacheManager);
    }
}
