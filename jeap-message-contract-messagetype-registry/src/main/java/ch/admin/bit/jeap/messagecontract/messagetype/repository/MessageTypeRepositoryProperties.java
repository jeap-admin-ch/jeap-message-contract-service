package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "messages")
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class MessageTypeRepositoryProperties {

    static final Duration DEFAULT_GIT_TIMEOUT = Duration.ofMinutes(3);

    private List<RepositoryProperties> repositories;

    /**
     * Timeout of the network operations of the git clones and fetches of message type registries, so that a registry
     * that stops responding does not block requests indefinitely. JGit applies it to each connect and to each read
     * from the network, not to the total duration of a clone or fetch: a slow transfer that keeps receiving data is
     * not aborted. The value is rounded up to whole seconds. A zero duration disables the timeout.
     */
    private Duration gitTimeout = DEFAULT_GIT_TIMEOUT;

    /**
     * @return {@link #getGitTimeout()} in the whole seconds expected by JGit, where zero means no timeout
     */
    int gitTimeoutSeconds() {
        return toGitTimeoutSeconds(gitTimeout);
    }

    static int toGitTimeoutSeconds(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            return 0;
        }
        long seconds = timeout.getNano() > 0 ? timeout.getSeconds() + 1 : timeout.getSeconds();
        return (int) Math.min(seconds, Integer.MAX_VALUE);
    }
}
