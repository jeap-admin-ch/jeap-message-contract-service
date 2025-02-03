package ch.admin.bit.jeap.messagecontract.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@AutoConfiguration
@EnableTransactionManagement
@EnableJpaRepositories
@ComponentScan
@EntityScan(basePackages = "ch.admin.bit.jeap.messagecontract")
public class PersistenceConfiguration {
}
