package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@ComponentScan
@EnableScheduling
@EnableConfigurationProperties({MessageTypeRepositoryProperties.class, MessageTypeRepositoryCacheProperties.class})
public class MessageTypeRepositoryConfiguration {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
