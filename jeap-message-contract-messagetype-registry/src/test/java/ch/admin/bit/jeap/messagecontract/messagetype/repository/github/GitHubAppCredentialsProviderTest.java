package ch.admin.bit.jeap.messagecontract.messagetype.repository.github;

import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubAppCredentialsProviderTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GitHubAppCredentialsProvider provider;
    private static final String TEST_APP_ID = "12345";
    private static final String TEST_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
                                    MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDDT39oXdT/DMfR
                                    kI77aUEA50Y1MxVbwjPkeiS7uZXB7QxX1fS54WX67VWRDwA9nBTjQbg5is2Wne0v
                                    +3TM0Tmd1MPkuGfCKQcJgALSxRjWT1+AD4yIzSg4AVAUd6wWwdw9HVCAQemQ1P5m
                                    sdWRi9b3I2j7IlHZfG7K3A8YHxycfjVZW9GTvx2MuOpT9oTMaewJ789y6Pf00trY
                                    FvLoAMBP+OWv58YJmapsRXOKlWdZv0GeiyDXV43/6VpU0fL9HyJ/KihL2BwfwFuj
                                    MQ671qKOr1/uQfluB0P0Gf3Ae9ogoFJErCnuLwh3VeJdQsCRfFfWGYv4SAVkaqxN
                                    VS2JCAVzAgMBAAECggEAGyspWX7H/Myt1SCLSzzKfpItaYQYIgIDTvKQvo5j3yyW
                                    7XcFSoAou/2czAdurKNUIoLHWjXNQHjqgCS2DDHEloh80Ym3YUJsyK0Gd4RUXqd+
                                    7OT80yDayeOg3KADD81a0iOMSbMhhvSiCO4O++acehdyaJDPGvZcwRpYfS4CwF/t
                                    1nppsjD+z+lsp7I30qnIVjzDrh1qTLSfKWXa6nDBe9Nimg4hL5U28L2HnUdbEMrl
                                    Bf4HOkJ7EATJoNuB2jU513uMEEuFdK48FLh3Kzvu0ei4fOxVqueyKTFjJCE8czI9
                                    qXqOlGT/oXM4ir5eC808Ekdd51Mu6w3GqCnNwBzmgQKBgQDmJz96a06PbUhSA4gz
                                    WYDB/3m8xj3Ce3uKBRy05FWpAySPBI+faJTv7lsNgnNHzAH5Xh2fDB3iHjzo1JNT
                                    hTBI0Y5tgn84e+UHFOfPQY+zYbcOovFTsYHIT7kXicqmOTkhhNOWPQRZirjlAyZB
                                    DCd8DeJzNGv7XSg0LeK4AwpB8wKBgQDZPoskm/zG/UXP0BOS83ZiIcssseOgPAAd
                                    Ml43Nd9Lrs69CkP3zEtEZg/K3J9pKemnwhcRUlEfvlobGxfdhXdAi/+gVrRQUlxI
                                    47Fq19NvkPN5+r3hOBwJgpwc1KMPzsjm7rT7kJrLuQTG7p63JPpUP75X8RwK+fjO
                                    lfbAOJiOgQKBgB06kMo4RILcixm3Tx3OWRbKHijGOGOxkO/nVEz4zpQnTQZIuwCw
                                    pHGQIMonbgKJOxrzQ+nF/SmRU4TyMj+iI46r5l2r/AItYdmzYvkkR16tozTdpq5N
                                    VcEdttDxc+YGUGYcW06yMxI4FuEmtD4AkCcmEEM3PhtvKkLuLOPXpv7XAoGAUOYG
                                    gMKJ1jw4xBNzRpTdL9vvwhwYbPILBNRd22d3WMKnACSTfPKZ0MXE+cFAp7PQ7ATN
                                    /EhQJ2cGPPPQ5lAuQV4g+j9vdD3HWelYhzYJ6ZDr6i+ih/0SC8SUh/PzKQ7TJ5Qa
                                    11dZHaYvjjkL552gjsESC8OgssG1kpCry5cH0IECgYAuTYf7FsqlTBacN5dnryb1
                                    vm/PkMV911wNzbSFxic3fQz/gd2s+XA+NfmLIAzIBu5NNJHkSQGLyGy8ZMq0gWOD
                                    F2lNwVvV7XnaG+yhu0eE9rw4sVfvYnpwF0v7IRCSVplr+Kdx5AhC1Q2YurIzgKIf
                                    vjuDAeEmN3Ee7aH8b3QeOw==
                                    -----END PRIVATE KEY-----""";

    @BeforeEach
    void setUp() {
        provider = new GitHubAppCredentialsProvider(TEST_APP_ID, TEST_PRIVATE_KEY);
        ReflectionTestUtils.setField(provider, "httpClient", httpClient);
    }

    @Test
    void testGetCredentialsSuccessful() throws Exception {
        // Mock installation ID response
        String installationResponse = """
            {
                "id": 123456,
                "account": {
                    "login": "testorg"
                }
            }""";
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(installationResponse);

        // Mock access token response
        String tokenResponse = """
            {
                "token": "ghs_123456789",
                "expires_at": "2023-12-31T23:59:59Z"
            }""";
        HttpResponse<String> tokenHttpResponse = mock(HttpResponse.class);
        when(tokenHttpResponse.statusCode()).thenReturn(201);
        when(tokenHttpResponse.body()).thenReturn(tokenResponse);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse)
                .thenReturn(tokenHttpResponse);

        URIish uri = new URIish("https://github.com/testorg/testrepo.git");
        CredentialItem.Username username = new CredentialItem.Username();
        CredentialItem.Password password = new CredentialItem.Password();

        boolean result = provider.get(uri, username, password);

        assertTrue(result);
        assertEquals("x-access-token", username.getValue());
        assertEquals("ghs_123456789", new String(password.getValue()));
    }

    @Test
    void testGetCredentialsFailedInstallationId() throws Exception {
        when(httpResponse.statusCode()).thenReturn(404);
        when(httpResponse.body()).thenReturn("Not Found");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        URIish uri = new URIish("https://github.com/testorg/testrepo.git");
        CredentialItem.Username username = new CredentialItem.Username();
        CredentialItem.Password password = new CredentialItem.Password();

        boolean result = provider.get(uri, username, password);

        assertFalse(result);
    }

    @Test
    void testInvalidUri() throws Exception {
        URIish uri = new URIish("https://github.com/invalid");
        CredentialItem.Username username = new CredentialItem.Username();
        CredentialItem.Password password = new CredentialItem.Password();

        boolean result = provider.get(uri, username, password);

        assertFalse(result);
    }
}
