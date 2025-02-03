package ch.admin.bit.jeap.messagecontract.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;

@AutoConfiguration
@EnableConfigurationProperties
@ComponentScan
@PropertySource("classpath:messageContractDefaultProperties.properties")
class MessageContractConfig {

}
