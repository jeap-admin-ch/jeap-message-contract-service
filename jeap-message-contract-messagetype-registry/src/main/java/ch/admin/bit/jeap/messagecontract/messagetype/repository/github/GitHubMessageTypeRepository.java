package ch.admin.bit.jeap.messagecontract.messagetype.repository.github;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;

public class GitHubMessageTypeRepository extends MessageTypeRepository {

    public GitHubMessageTypeRepository(String gitUri, Map<String, String> parameters, MeterRegistry meterRegistry) {
        super(gitUri);
        super.setCredentialsProvider(GitHubAppCredentialsProvider.fromParameters(parameters, meterRegistry));
    }

}
