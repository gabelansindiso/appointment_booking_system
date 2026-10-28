package com.nsindiso.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceGeneratorTest {

    private final ReferenceGenerator generator = new ReferenceGenerator();

    @Test
    void referencesHaveExpectedFormatWithoutAmbiguousCharacters () {
        for (int i = 0; i < 1_000; i++) {
            // no 0/O, 1/I/L
            assertThat(generator.next()).matches("CAP-[A-HJKMNP-Z2-9]{6}");
        }
    }

    @Test
    void referencesAreEffectivelyUnique () {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) seen.add(generator.next());

        // 31^6 ~ 887M combinations; a handful of collisions would still indicate a broken RNG
        assertThat(seen).hasSizeGreaterThan(9_990);
    }
}
