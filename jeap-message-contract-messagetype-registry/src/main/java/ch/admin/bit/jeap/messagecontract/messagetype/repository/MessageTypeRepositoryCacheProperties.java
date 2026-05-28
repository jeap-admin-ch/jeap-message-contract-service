package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "messages.repository-cache")
@NoArgsConstructor
@AllArgsConstructor
public class MessageTypeRepositoryCacheProperties {

    private boolean enabled = true;

    private String directory = System.getProperty("java.io.tmpdir") + "/message-type-repository-cache";

    private String refreshCron = "0 0 1 * * *";
}
