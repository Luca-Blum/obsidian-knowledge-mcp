package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** claude.ai's way in, step by step as its connector takes it: discovery, registration, consent, tokens, MCP. */
@QuarkusTest
class OAuthFlowTest {

    private static final String ISSUER = "https://knowledge-test.example.ts.net";

    private static final String PASSPHRASE = "test-passphrase-long-enough";

    private static final String CALLBACK = "https://claude.ai/api/mcp/auth_callback";

    /** The PKCE example of RFC 7636, appendix B. */
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @TestHTTPResource("/")
    URI root;

    private final HttpClient http = HttpClient.newHttpClient();

    private String clientId;

    @BeforeEach
    void registerClaudeAi() throws Exception {
        clientId = register(new JsonObject()
                .put("client_name", "Claude")
                .put("redirect_uris", new JsonArray().add(CALLBACK))
                .put("token_endpoint_auth_method", "none"))
                .getString("client_id");
    }

    @Test
    void whenConnect_givenNoToken_thenPointToTheResourceMetadata() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(root.resolve("/mcp"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/json"));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains(
                "Bearer resource_metadata=\"" + ISSUER + "/.well-known/oauth-protected-resource\"");
    }

    @Test
    void whenDiscover_givenNoToken_thenDescribeTheServerAtTheOriginRoot() throws Exception {
        JsonObject resource = getJson("/.well-known/oauth-protected-resource");
        JsonObject server = getJson("/.well-known/oauth-authorization-server");

        assertThat(resource.getString("resource")).isEqualTo(ISSUER + "/mcp");
        assertThat(resource.getJsonArray("authorization_servers")).containsExactly(ISSUER);
        assertThat(server.getString("issuer")).isEqualTo(ISSUER);
        assertThat(server.getString("authorization_endpoint")).isEqualTo(ISSUER + "/authorize");
        assertThat(server.getString("token_endpoint")).isEqualTo(ISSUER + "/token");
        assertThat(server.getString("registration_endpoint")).isEqualTo(ISSUER + "/register");
        assertThat(server.getJsonArray("code_challenge_methods_supported")).containsExactly("S256");
    }

    @Test
    void whenListTools_givenAccessTokenFromTheWholeFlow_thenFindEveryTool() throws Exception {
        String accessToken = redeem(consent(PASSPHRASE)).getString("access_token");

        TestClients.connectWith(accessToken)
                .when()
                .toolsList(page -> assertThat(page.findByName("search")).isNotNull())
                .thenAssertResults();
    }

    @Test
    void whenOpenConsentPage_givenRegisteredClient_thenShowItsNameAndTheCollection() throws Exception {
        HttpResponse<String> page = send(HttpRequest.newBuilder(authorizeUri(CALLBACK)));

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Claude", "test", "passphrase");
        assertThat(page.headers().firstValue("X-Frame-Options")).contains("DENY");
    }

    @Test
    void whenOpenConsentPage_givenRedirectThatWasNotRegistered_thenShowErrorWithoutRedirecting() throws Exception {
        HttpResponse<String> page = send(HttpRequest.newBuilder(authorizeUri("https://attacker.example/callback")));

        assertThat(page.statusCode()).isEqualTo(400);
        assertThat(page.headers().firstValue("Location")).isEmpty();
    }

    @Test
    void whenOpenConsentPage_givenNoCodeChallenge_thenRedirectWithInvalidRequest() throws Exception {
        URI withoutChallenge = root.resolve("/authorize?" + form(Map.of(
                "response_type", "code", "client_id", clientId, "redirect_uri", CALLBACK, "state", "xyz")));

        HttpResponse<String> response = send(HttpRequest.newBuilder(withoutChallenge));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(query(response)).containsEntry("error", "invalid_request").containsEntry("state", "xyz");
    }

    @Test
    void whenConsent_givenWrongPassphrase_thenShowThePageAgainWithoutCode() throws Exception {
        HttpResponse<String> response = postConsent("wrong-passphrase-of-any-length");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.body()).contains("passphrase");
    }

    @Test
    void whenConsent_givenRightPassphrase_thenRedirectWithCodeAndState() throws Exception {
        HttpResponse<String> response = postConsent(PASSPHRASE);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).hasValueSatisfying(
                location -> assertThat(location).startsWith(CALLBACK + "?"));
        assertThat(query(response)).containsKey("code").containsEntry("state", "xyz");
    }

    @Test
    void whenRedeem_givenWrongVerifier_thenRejectAsInvalidGrant() throws Exception {
        HttpResponse<String> response = token(Map.of("grant_type", "authorization_code", "code", consent(PASSPHRASE),
                "redirect_uri", CALLBACK, "client_id", clientId, "code_verifier", VERIFIER + "x"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(new JsonObject(response.body()).getString("error")).isEqualTo("invalid_grant");
    }

    @Test
    void whenRedeem_givenCodeRedeemedBefore_thenRejectAsInvalidGrant() throws Exception {
        String code = consent(PASSPHRASE);
        redeem(code);

        HttpResponse<String> response = token(redemption(code));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(new JsonObject(response.body()).getString("error")).isEqualTo("invalid_grant");
    }

    @Test
    void whenRedeem_givenCode_thenAnswerTokensThatAreNeverCached() throws Exception {
        HttpResponse<String> response = token(redemption(consent(PASSPHRASE)));

        JsonObject tokens = new JsonObject(response.body());
        assertThat(tokens.getString("token_type")).isEqualTo("Bearer");
        assertThat(tokens.getInteger("expires_in")).isEqualTo(3600);
        assertThat(tokens.getString("refresh_token")).isNotBlank();
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    }

    @Test
    void whenRefresh_givenRefreshToken_thenIssueNewTokensAndRetireTheOldRefreshToken() throws Exception {
        String refreshToken = redeem(consent(PASSPHRASE)).getString("refresh_token");
        Map<String, String> refresh = Map.of("grant_type", "refresh_token", "refresh_token", refreshToken,
                "client_id", clientId);

        HttpResponse<String> first = token(refresh);
        HttpResponse<String> second = token(refresh);

        assertThat(first.statusCode()).isEqualTo(200);
        TestClients.connectWith(new JsonObject(first.body()).getString("access_token"))
                .when()
                .toolsList(page -> assertThat(page.findByName("search")).isNotNull())
                .thenAssertResults();
        assertThat(second.statusCode()).isEqualTo(400);
    }

    @Test
    void whenRedeem_givenConfidentialClientAuthenticatingWithBasic_thenIssueTokens() throws Exception {
        JsonObject registration = register(new JsonObject()
                .put("client_name", "CLI")
                .put("redirect_uris", new JsonArray().add(CALLBACK)));
        String confidentialId = registration.getString("client_id");
        String secret = registration.getString("client_secret");
        String code = consent(confidentialId, PASSPHRASE);
        String basic = Base64.getEncoder().encodeToString(
                (confidentialId + ":" + secret).getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> response = send(HttpRequest.newBuilder(root.resolve("/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + basic)
                .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("grant_type", "authorization_code",
                        "code", code, "redirect_uri", CALLBACK, "code_verifier", VERIFIER)))));

        assertThat(registration.getString("token_endpoint_auth_method")).isEqualTo("client_secret_basic");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void whenRegister_givenUnsafeRedirect_thenRejectAsInvalidRedirectUri() throws Exception {
        HttpResponse<String> response = post("/register", "application/json", new JsonObject()
                .put("redirect_uris", new JsonArray().add("http://attacker.example/callback")).encode());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(new JsonObject(response.body()).getString("error")).isEqualTo("invalid_redirect_uri");
    }

    @Test
    void whenConnect_givenUnknownAccessToken_thenRejectAsUnauthorized() {
        TestClients.expectRejected("not-an-access-token");
    }

    private JsonObject register(JsonObject metadata) throws Exception {
        HttpResponse<String> response = post("/register", "application/json", metadata.encode());
        assertThat(response.statusCode()).isEqualTo(201);
        return new JsonObject(response.body());
    }

    private String consent(String passphrase) throws Exception {
        return consent(clientId, passphrase);
    }

    /** Gives the passphrase on the consent page and returns the code from the redirect. */
    private String consent(String client, String passphrase) throws Exception {
        HttpResponse<String> response = post("/authorize", "application/x-www-form-urlencoded",
                form(consentForm(client, passphrase)));
        assertThat(response.statusCode()).isEqualTo(302);
        return query(response).get("code");
    }

    private HttpResponse<String> postConsent(String passphrase) throws Exception {
        return post("/authorize", "application/x-www-form-urlencoded", form(consentForm(clientId, passphrase)));
    }

    private static Map<String, String> consentForm(String client, String passphrase) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("client_id", client);
        fields.put("redirect_uri", CALLBACK);
        fields.put("code_challenge", CHALLENGE);
        fields.put("state", "xyz");
        fields.put("passphrase", passphrase);
        return fields;
    }

    private JsonObject redeem(String code) throws Exception {
        HttpResponse<String> response = token(redemption(code));
        assertThat(response.statusCode()).isEqualTo(200);
        return new JsonObject(response.body());
    }

    private Map<String, String> redemption(String code) {
        return Map.of("grant_type", "authorization_code", "code", code, "redirect_uri", CALLBACK,
                "client_id", clientId, "code_verifier", VERIFIER);
    }

    private HttpResponse<String> token(Map<String, String> fields) throws Exception {
        return post("/token", "application/x-www-form-urlencoded", form(fields));
    }

    private URI authorizeUri(String redirectUri) {
        return root.resolve("/authorize?" + form(Map.of("response_type", "code", "client_id", clientId,
                "redirect_uri", redirectUri, "code_challenge", CHALLENGE, "code_challenge_method", "S256",
                "state", "xyz")));
    }

    private JsonObject getJson(String path) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(root.resolve(path)));
        assertThat(response.statusCode()).isEqualTo(200);
        return new JsonObject(response.body());
    }

    private HttpResponse<String> post(String path, String contentType, String body) throws Exception {
        return send(HttpRequest.newBuilder(root.resolve(path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(field -> field.getKey() + "=" + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /** The query parameters of the redirect a response points to. */
    private static Map<String, String> query(HttpResponse<String> response) {
        String location = response.headers().firstValue("Location").orElseThrow();
        return Arrays.stream(URI.create(location).getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
