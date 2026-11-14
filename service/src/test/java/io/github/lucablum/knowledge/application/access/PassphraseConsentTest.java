package io.github.lucablum.knowledge.application.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.domain.access.AuthorizationCode;
import io.github.lucablum.knowledge.domain.access.CodeChallenge;
import io.github.lucablum.knowledge.domain.access.Grant;
import io.github.lucablum.knowledge.domain.access.IssuedTokens;
import io.github.lucablum.knowledge.domain.access.Passphrase;
import io.github.lucablum.knowledge.domain.access.RegisteredClient;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PassphraseConsentTest {

    private static final String PASSPHRASE = "correct-horse-battery";

    private static final URI CALLBACK = URI.create("https://claude.ai/api/mcp/auth_callback");

    /** The PKCE example of RFC 7636, appendix B. */
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    private static final CodeChallenge CHALLENGE = new CodeChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");

    private final InMemoryGrants grants = new InMemoryGrants();
    private final MovableClock clock = new MovableClock();
    private AuthorizeClients consent;
    private RegisteredClient client;

    @BeforeEach
    void registerClient() {
        consent = consentWith(PASSPHRASE);
        client = consent.register(new ClientRegistration("claude.ai", List.of(CALLBACK), false)).client();
    }

    @Test
    void whenRegister_givenConfidentialClient_thenReturnSecretOnceAndStoreOnlyItsHash() {
        Registration registration = consent.register(new ClientRegistration("cli", List.of(CALLBACK), true));

        String secret = registration.secret().orElseThrow();
        RegisteredClient stored = consent.client(registration.client().clientId()).orElseThrow();
        assertThat(stored.secret()).hasValueSatisfying(hash -> assertThat(hash.value()).isNotEqualTo(secret));
        assertThat(stored.authenticates(Optional.of(secret))).isTrue();
    }

    @Test
    void whenConsent_givenWrongPassphrase_thenIssueNoCode() {
        assertThat(consent.consent(request(), "wrong-horse-battery-staple")).isEmpty();
    }

    @Test
    void whenConsent_givenRedirectThatWasNotRegistered_thenIssueNoCode() {
        AuthorizationRequest elsewhere =
                new AuthorizationRequest(client.clientId(), URI.create("https://attacker.example/cb"), CHALLENGE);

        assertThat(consent.consent(elsewhere, PASSPHRASE)).isEmpty();
    }

    @Test
    void whenRedeem_givenCodeAndVerifier_thenIssueTokensThatAreAdmitted() {
        IssuedTokens tokens = consent.redeem(redemption(code(), VERIFIER)).orElseThrow();

        assertThat(consent.admits(tokens.accessToken())).isTrue();
        assertThat(tokens.expiresIn()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void whenRedeem_givenWrongVerifier_thenIssueNothing() {
        assertThat(consent.redeem(redemption(code(), VERIFIER + "x"))).isEmpty();
    }

    @Test
    void whenRedeem_givenCodeRedeemedBefore_thenIssueNothing() {
        String code = code();
        consent.redeem(redemption(code, VERIFIER));

        assertThat(consent.redeem(redemption(code, VERIFIER))).isEmpty();
    }

    @Test
    void whenRedeem_givenExpiredCode_thenIssueNothing() {
        String code = code();
        clock.advance(AuthorizationCode.LIFETIME);

        assertThat(consent.redeem(redemption(code, VERIFIER))).isEmpty();
    }

    @Test
    void whenRedeem_givenAnotherClientsCode_thenIssueNothing() {
        RegisteredClient other = consent.register(new ClientRegistration("other", List.of(CALLBACK), false)).client();
        CodeRedemption asOther =
                new CodeRedemption(code(), new ClientCredentials(other.clientId(), Optional.empty()), CALLBACK, VERIFIER);

        assertThat(consent.redeem(asOther)).isEmpty();
    }

    @Test
    void whenRedeem_givenConfidentialClientWithoutItsSecret_thenIssueNothing() {
        Registration registration = consent.register(new ClientRegistration("cli", List.of(CALLBACK), true));
        String clientId = registration.client().clientId();
        String code = consent.consent(new AuthorizationRequest(clientId, CALLBACK, CHALLENGE), PASSPHRASE).orElseThrow();

        CodeRedemption withoutSecret =
                new CodeRedemption(code, new ClientCredentials(clientId, Optional.empty()), CALLBACK, VERIFIER);
        assertThat(consent.redeem(withoutSecret)).isEmpty();
    }

    @Test
    void whenAdmit_givenAccessTokenOlderThanAnHour_thenRefuse() {
        IssuedTokens tokens = tokens();
        clock.advance(Grant.ACCESS_LIFETIME);

        assertThat(consent.admits(tokens.accessToken())).isFalse();
    }

    @Test
    void whenAdmit_givenUnknownToken_thenRefuse() {
        assertThat(consent.admits("not-a-token")).isFalse();
    }

    @Test
    void whenRefresh_givenRefreshToken_thenIssueNewTokensAndRetireTheOldOnes() {
        IssuedTokens first = tokens();

        IssuedTokens second = consent.refresh(first.refreshToken(), credentials()).orElseThrow();

        assertThat(consent.admits(second.accessToken())).isTrue();
        assertThat(consent.admits(first.accessToken())).isFalse();
        assertThat(consent.refresh(first.refreshToken(), credentials())).isEmpty();
    }

    @Test
    void whenRefresh_givenRefreshTokenOlderThanThirtyDays_thenIssueNothing() {
        IssuedTokens tokens = tokens();
        clock.advance(Grant.REFRESH_LIFETIME);

        assertThat(consent.refresh(tokens.refreshToken(), credentials())).isEmpty();
    }

    @Test
    void whenRefresh_givenAnotherClientsRefreshToken_thenIssueNothing() {
        IssuedTokens tokens = tokens();

        assertThat(consent.refresh(tokens.refreshToken(), new ClientCredentials("other", Optional.empty()))).isEmpty();
    }

    @Test
    void whenStart_givenTheSamePassphraseAsBefore_thenKeepGrants() {
        consent.forgetGrantsIfPassphraseChanged();
        IssuedTokens tokens = tokens();

        consentWith(PASSPHRASE).forgetGrantsIfPassphraseChanged();

        assertThat(consent.admits(tokens.accessToken())).isTrue();
    }

    @Test
    void whenStart_givenAnotherPassphrase_thenForgetEveryGrant() {
        consent.forgetGrantsIfPassphraseChanged();
        IssuedTokens tokens = tokens();

        AuthorizeClients restarted = consentWith("another-horse-battery-staple");
        restarted.forgetGrantsIfPassphraseChanged();

        assertThat(restarted.admits(tokens.accessToken())).isFalse();
        assertThat(restarted.refresh(tokens.refreshToken(), credentials())).isEmpty();
        assertThat(restarted.client(client.clientId())).isPresent();
    }

    private AuthorizeClients consentWith(String passphrase) {
        return new PassphraseConsent(grants, new Passphrase(passphrase), clock);
    }

    private IssuedTokens tokens() {
        return consent.redeem(redemption(code(), VERIFIER)).orElseThrow();
    }

    private String code() {
        return consent.consent(request(), PASSPHRASE).orElseThrow();
    }

    private AuthorizationRequest request() {
        return new AuthorizationRequest(client.clientId(), CALLBACK, CHALLENGE);
    }

    private CodeRedemption redemption(String code, String verifier) {
        return new CodeRedemption(code, credentials(), CALLBACK, verifier);
    }

    private ClientCredentials credentials() {
        return new ClientCredentials(client.clientId(), Optional.empty());
    }
}
