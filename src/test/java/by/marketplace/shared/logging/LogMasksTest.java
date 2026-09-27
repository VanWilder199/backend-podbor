package by.marketplace.shared.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMasksTest {

    @Test
    void phone_masksMiddle() {
        assertThat(LogMasks.phone("+375291234567")).isEqualTo("+375*****4567");
    }

    @Test
    void phone_shortValueReturnedAsIs() {
        assertThat(LogMasks.phone("+3752999")).isEqualTo("+3752999");
    }

    @Test
    void phone_emptyStringReturnedAsIs() {
        assertThat(LogMasks.phone("")).isEmpty();
    }

    @Test
    void phone_nullReturnedNull() {
        assertThat(LogMasks.phone(null)).isNull();
    }

    @Test
    void email_masksLocalPart() {
        assertThat(LogMasks.email("ivan.petrov@mail.by")).isEqualTo("i***@mail.by");
    }

    @Test
    void email_shortLocalPartReturnedAsIs() {
        assertThat(LogMasks.email("a@b.c")).isEqualTo("a@b.c");
    }

    @Test
    void email_emptyStringReturnedAsIs() {
        assertThat(LogMasks.email("")).isEmpty();
    }

    @Test
    void destination_detectsEmail() {
        assertThat(LogMasks.destination("ivan.petrov@mail.by")).isEqualTo("i***@mail.by");
    }

    @Test
    void destination_detectsPhone() {
        assertThat(LogMasks.destination("+375291234567")).isEqualTo("+375*****4567");
    }

    @Test
    void destination_nullReturnedNull() {
        assertThat(LogMasks.destination(null)).isNull();
    }
}