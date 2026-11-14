package io.github.lucablum.knowledge.application.search;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchSettingsTest {

    @Test
    void whenCreate_givenDefaultKAboveMaxK_thenRejectWithIllegalArgument() {
        assertThatThrownBy(() -> new SearchSettings(25, 20, 0.5, 2, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("defaultK must be between 1 and maxK (20), but is 25");
    }

    @Test
    void whenCreate_givenDefaultKBelowOne_thenRejectWithIllegalArgument() {
        assertThatThrownBy(() -> new SearchSettings(0, 20, 0.5, 2, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("defaultK must be between 1 and maxK (20), but is 0");
    }
}
