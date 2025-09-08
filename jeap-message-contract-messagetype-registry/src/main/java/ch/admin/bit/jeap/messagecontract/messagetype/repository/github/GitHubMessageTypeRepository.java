package ch.admin.bit.jeap.messagecontract.messagetype.repository.github;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;

import java.util.Map;

public class GitHubMessageTypeRepository extends MessageTypeRepository {

    public GitHubMessageTypeRepository(String gitUri, Map<String, String> parameters) {
        super(gitUri);
        assertRequiredParameters(parameters);
        super.setCredentialsProvider(new GitHubAppCredentialsProvider(
                parameters.get("GITHUB_APP_ID"),
                parameters.get("GITHUB_PRIVATE_KEY_PEM")
        ));
    }

    private void assertRequiredParameters(Map<String, String> parameters) {
        if (parameters == null || !parameters.containsKey("GITHUB_APP_ID")) {
            throw new IllegalArgumentException("Missing required parameter 'GITHUB_APP_ID' for GitHub repository");
        }
        if (!parameters.containsKey("GITHUB_PRIVATE_KEY_PEM")) {
            throw new IllegalArgumentException("Missing required parameter 'GITHUB_PRIVATE_KEY_PEM' for GitHub repository");
        }
    }

}
