package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan
@EnableConfigurationProperties(MessageTypeRepositoryProperties.class)
public class MessageTypeRepositoryConfiguration {
}
