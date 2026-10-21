package ch.admin.bit.jeap.messagecontract.domain.renovate;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "jeap.messagecontract.renovate")
public class RenovateProperties {

    /**
     * How long the deployed contracts, their schemas and the message type registry data used to answer Renovate
     * datasource requests are reused. Renovate queries many message types at once for many repositories, and
     * without reuse each request queries the database and reads the registry again. Within this time, newly
     * deployed contracts and newly published message type versions may not yet be taken into account. A zero
     * duration disables the reuse.
     */
    private Duration cacheTtl = Duration.ofMinutes(5);
}
