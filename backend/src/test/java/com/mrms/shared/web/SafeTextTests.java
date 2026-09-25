package com.mrms.shared.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeTextTests {

    @Test
    void keepsOrdinaryTextIncludingHindiAndLineBreaks() {
        assertThat(SafeText.clean("Paracetamol 500 mg\nदवा उपलब्ध नहीं\tx")).isEqualTo("Paracetamol 500 mg\nदवा उपलब्ध नहीं\tx");
        assertThat(SafeText.clean(null)).isNull();
    }

    @Test
    void normalisesToComposedForm() {
        // "e" followed by a combining acute accent becomes the single character
        assertThat(SafeText.clean("Café")).isEqualTo("Café");
    }

    @Test
    void refusesControlAndDirectionCharacters() {
        for (String bad : new String[]{"a\u0000b", "a\u001Bb", "a\u0085b", "a‮b", "a⁦b", "a￾b", "a\uD800b"}) {
            assertThatThrownBy(() -> SafeText.clean(bad))
                    .isInstanceOf(SafeText.UnsafeTextException.class)
                    .hasMessageContaining("not allowed");
        }
    }

    @Test
    void stripReplacesInsteadOfRefusing() {
        assertThat(SafeText.strip("a‮b\u0007c", '_')).isEqualTo("a_b_c");
        assertThat(SafeText.strip("clean.pdf", '_')).isEqualTo("clean.pdf");
    }
}
