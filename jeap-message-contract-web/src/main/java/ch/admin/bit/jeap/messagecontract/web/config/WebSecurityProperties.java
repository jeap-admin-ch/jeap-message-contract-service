package ch.admin.bit.jeap.messagecontract.web.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "jeap.messagecontract.users", ignoreUnknownFields = false)
@Slf4j
public class WebSecurityProperties {

    @NestedConfigurationProperty
    private List<UserProperty> writeUsers;

    @NestedConfigurationProperty
    private List<UserProperty> uploadContractUsers;

    @PostConstruct
    void init() {
        log.info("Load security configuration: {}", this);
    }
}
