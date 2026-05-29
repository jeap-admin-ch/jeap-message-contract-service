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

    /**
     * Minimum interval between two on-demand cache refreshes for the same repository. Used to debounce
     * the per-request "refresh before resolving a branch" path so that a batch of concurrent uploads
     * does not trigger one upstream fetch per request. A refresh request arriving within this window of
     * a previous successful refresh is a no-op.
     */
    private long refreshDebounceMillis = 5_000L;
}
