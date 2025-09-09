package ch.admin.bit.jeap.messagecontract.messagetype.repository.github;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

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

/**
 * A JGit CredentialsProvider that uses GitHub App authentication.
 * This provider generates installation access tokens using JWT authentication.
 */
@Slf4j
public class GitHubAppCredentialsProvider extends CredentialsProvider {

    static {
        java.security.Security.addProvider(
                new org.bouncycastle.jce.provider.BouncyCastleProvider()
        );
    }

    private final String appId;
    private final PrivateKey privateKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new GitHub App credentials provider.
     *
     * @param appId GitHub App ID
     * @param privateKeyPem GitHub App private key in PEM format
     */
    public GitHubAppCredentialsProvider(String appId, String privateKeyPem) {
        this.appId = appId;
        this.privateKey = parsePrivateKey(privateKeyPem);
        this.httpClient = HttpClient.newBuilder().build();

        this.objectMapper = new ObjectMapper();
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
        try {
            // Extract owner/repo from URI
            String[] pathParts = uri.getPath().split("/");
            if (pathParts.length < 3) {
                return false;
            }

            String owner = pathParts[1];
            String repo = pathParts[2].replace(".git", "");

            // Get installation ID for this repository
            Long installationId = getInstallationId(owner, repo);
            if (installationId == null) {
                return false;
            }

            // Get or create installation access token
            String accessToken = getInstallationAccessToken(installationId);
            if (accessToken == null) {
                return false;
            }

            // Set credentials
            for (CredentialItem item : items) {
                if (item instanceof CredentialItem.Username) {
                    ((CredentialItem.Username) item).setValue("x-access-token");
                } else if (item instanceof CredentialItem.Password) {
                    ((CredentialItem.Password) item).setValue(accessToken.toCharArray());
                } else {
                    throw new UnsupportedCredentialItem(uri, item.getPromptText());
                }
            }

            return true;

        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gets the installation ID for a specific repository
     */
    private Long getInstallationId(String owner, String repo) throws Exception {
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
            JsonNode jsonNode = objectMapper.readTree(response.body());
            return jsonNode.get("id").asLong();
        } else {
            log.warn("Failed to get installation ID: {} - {}", response.statusCode(), response.body());
        }

        return null;
    }

    private String getInstallationAccessToken(Long installationId) throws Exception {
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
            JsonNode jsonNode = objectMapper.readTree(response.body());
            return jsonNode.get("token").asText();
        } else {
            log.warn("Failed to get installation access token: {} - {}", response.statusCode(), response.body());
        }
        return null;
    }

    private String createAppJWT() throws Exception {
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
            throw new RuntimeException(e);
        }
    }

}
