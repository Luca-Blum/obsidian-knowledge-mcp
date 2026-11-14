package io.github.lucablum.knowledge.domain.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PKCE with S256, checked against the example of RFC 7636, appendix B. */
class CodeChallengeTest {

    static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    static final CodeChallenge CHALLENGE = new CodeChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");

    @Test
    void whenCheckVerifier_givenTheVerifierOfTheChallenge_thenMet() {
        assertThat(CHALLENGE.isMetBy(VERIFIER)).isTrue();
    }

    @Test
    void whenCheckVerifier_givenAnotherOrNoVerifier_thenNotMet() {
        assertThat(CHALLENGE.isMetBy(VERIFIER + "x")).isFalse();
        assertThat(CHALLENGE.isMetBy(null)).isFalse();
    }
}
