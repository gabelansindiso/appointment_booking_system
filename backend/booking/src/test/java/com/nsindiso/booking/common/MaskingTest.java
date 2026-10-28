package com.nsindiso.booking.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MaskingTest {

    @ParameterizedTest
    @CsvSource({
            "jane.doe@example.com, j***@example.com",
            "a@b.co,               a***@b.co",
            "@example.com,         ***",
            "not-an-email,         ***",
    })
    void masksEmailLocalPart (String input, String expected) {
        assertThat(Masking.email(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "+27821234567, ********4567",
            "0821234567,   ******4567",
            "12345,        *2345",
            "1234,         ****",
            "12,           ****",
    })
    void masksAllButLastFourPhoneDigits (String input, String expected) {
        assertThat(Masking.phone(input)).isEqualTo(expected);
    }
}
