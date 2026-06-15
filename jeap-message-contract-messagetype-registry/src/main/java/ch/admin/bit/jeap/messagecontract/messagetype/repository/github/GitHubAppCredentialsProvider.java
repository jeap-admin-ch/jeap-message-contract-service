package ch.admin.bit.jeap.messagecontract.messagetype.repository.github;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static ch.admin.bit.jeap.messagecontract.messagetype.repository.Elapsed.elapsedMs;

/**
 * A JGit CredentialsProvider that uses GitHub App authentication.
 * This provider generates installation access tokens using JWT authentication.
 */
@Slf4j
public class GitHubAppCredentialsProvider extends CredentialsProvider {

    public static final String GITHUB_APP_ID = "GITHUB_APP_ID";
    public static final String GITHUB_PRIVATE_KEY_PEM = "GITHUB_PRIVATE_KEY_PEM";

    static {
        java.security.Security.addProvider(
                new org.bouncycastle.jce.provider.BouncyCastleProvider()
        );
    }

    private final String appId;
    private final PrivateKey privateKey;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final Timer getTimer;

    /**
     * Builds a {@code GitHubAppCredentialsProvider} from the parameter map carried by
     * {@code RepositoryProperties.parameters} for {@code GITHUB}-typed repositories. Both
     * {@link #GITHUB_APP_ID} and {@link #GITHUB_PRIVATE_KEY_PEM} are required.
     *
     * @throws IllegalArgumentException if either parameter is missing
     */
    public static GitHubAppCredentialsProvider fromParameters(Map<String, String> parameters, MeterRegistry meterRegistry) {
        requireParameter(parameters, GITHUB_APP_ID);
        requireParameter(parameters, GITHUB_PRIVATE_KEY_PEM);
        return new GitHubAppCredentialsProvider(
                parameters.get(GITHUB_APP_ID),
                parameters.get(GITHUB_PRIVATE_KEY_PEM),
                meterRegistry);
    }

    private static void requireParameter(Map<String, String> parameters, String name) {
        if (parameters == null || !parameters.containsKey(name)) {
            throw new IllegalArgumentException("Missing required parameter '" + name + "' for GitHub repository");
        }
    }

    /**
     * Creates a new GitHub App credentials provider.
     *
     * @param appId GitHub App ID
     * @param privateKeyPem GitHub App private key in PEM format
     * @param meterRegistry Micrometer registry used to record credential-fetch timings
     */
    public GitHubAppCredentialsProvider(String appId, String privateKeyPem, MeterRegistry meterRegistry) {
        this.appId = appId;
        this.privateKey = parsePrivateKey(privateKeyPem);
        this.httpClient = HttpClient.newBuilder().build();

        this.jsonMapper = new JsonMapper();
        this.getTimer = Timer.builder("githubappcredentialsprovider.get.time")
                .description("Time taken to obtain GitHub App installation access token")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean supports(CredentialItem... items) {
        for (CredentialItem item : items) {
            if (item instanceof CredentialItem.Username || item instanceof CredentialItem.Password) {
                continue;
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
        Timer.Sample sample = Timer.start();
        long getStartNanos = System.nanoTime();
        try {
            // Extract owner/repo from URI
            String[] pathParts = uri.getPath().split("/");
            if (pathParts.length < 3) {
                return false;
            }

            String owner = pathParts[1];
            String repo = pathParts[2].replace(".git", "");
            log.debug("GitHubAppCredentials.get: owner={} repo={}", owner, repo);

            // Get installation ID for this repository
            long installationIdStart = System.nanoTime();
            Long installationId = getInstallationId(owner, repo);
            log.debug("GitHubAppCredentials.get: getInstallationId owner={} repo={} took {} ms (id={})",
                    owner, repo, elapsedMs(installationIdStart), installationId);
            if (installationId == null) {
                return false;
            }

            // Get or create installation access token
            long tokenStart = System.nanoTime();
            String accessToken = getInstallationAccessToken(installationId);
            log.debug("GitHubAppCredentials.get: getInstallationAccessToken installationId={} took {} ms",
                    installationId, elapsedMs(tokenStart));
            if (accessToken == null) {
                return false;
            }

            // Set credentials
            for (CredentialItem item : items) {
                switch (item) {
                    case CredentialItem.Username username -> username.setValue("x-access-token");
                    case CredentialItem.Password password -> password.setValue(accessToken.toCharArray());
                    default -> throw new UnsupportedCredentialItem(uri, item.getPromptText());
                }
            }

            log.info("GitHubAppCredentials.get: succeeded for {} in {} ms", uri, elapsedMs(getStartNanos));
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("GitHubAppCredentials.get: interrupted for {} after {} ms: {}", uri, elapsedMs(getStartNanos), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.warn("GitHubAppCredentials.get: failed for {} after {} ms: {}", uri, elapsedMs(getStartNanos), e.getMessage(), e);
            return false;
        } finally {
            sample.stop(getTimer);
        }
    }

    /**
     * Gets the installation ID for a specific repository
     */
    private Long getInstallationId(String owner, String repo) throws java.io.IOException, InterruptedException, JOSEException {
        String jwt = createAppJWT();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/installation"))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "GitHubApp-JGit/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode jsonNode = jsonMapper.readTree(response.body());
            return jsonNode.get("id").asLong();
        } else {
            log.warn("Failed to get installation ID: {} - {}", response.statusCode(), response.body());
        }

        return null;
    }

    private String getInstallationAccessToken(Long installationId) throws java.io.IOException, InterruptedException, JOSEException {
        String jwt = createAppJWT();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/app/installations/" + installationId + "/access_tokens"))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "GitHubApp-JGit/1.0")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 201) {
            JsonNode jsonNode = jsonMapper.readTree(response.body());
            return jsonNode.get("token").asString();
        } else {
            log.warn("Failed to get installation access token: {} - {}", response.statusCode(), response.body());
        }
        return null;
    }

    private String createAppJWT() throws JOSEException {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .build();

        Instant now = Instant.now();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(appId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600))) // 10 minutes
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claimsSet);
        RSASSASigner signer = new RSASSASigner(privateKey);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    private static PrivateKey parsePrivateKey(String privateKeyPem) {
        String privateKeyContent = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes, "RSA");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to parse GitHub App private key", e);
        }
    }

}
