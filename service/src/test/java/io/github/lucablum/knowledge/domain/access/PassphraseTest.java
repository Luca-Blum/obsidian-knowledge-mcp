package io.github.lucablum.knowledge.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PassphraseTest {

    private static final String LONG_ENOUGH = "correct-horse-battery";

    @Test
    void whenCreate_givenFewerThanTwentyCharacters_thenReject() {
        assertThatThrownBy(() -> new Passphrase("nineteen-characters"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20");
    }

    @Test
    void whenAdmit_givenTheSamePassphrase_thenAdmit() {
        assertThat(new Passphrase(LONG_ENOUGH).admits(LONG_ENOUGH)).isTrue();
    }

    @Test
    void whenAdmit_givenAnotherOrNoAttempt_thenRefuse() {
        Passphrase passphrase = new Passphrase(LONG_ENOUGH);

        assertThat(passphrase.admits("correct-horse-battery-staple")).isFalse();
        assertThat(passphrase.admits(null)).isFalse();
    }

    @Test
    void whenPrint_givenAPassphrase_thenHideIt() {
        assertThat(new Passphrase(LONG_ENOUGH).toString()).doesNotContain(LONG_ENOUGH);
    }
}
