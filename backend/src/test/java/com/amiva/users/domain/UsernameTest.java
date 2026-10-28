package com.amiva.users.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsernameTest {

    @Test
    void aceptaMinusculasDigitosYGuionesSueltos() {
        assertThat(Username.isValid("ana")).isTrue();
        assertThat(Username.isValid("nutria-dormilona-42")).isTrue();
        assertThat(Username.isValid("user-1a2b3c4d")).isTrue();
        assertThat(Username.isValid("007")).isTrue();
    }

    @Test
    void rechazaGuionesAlPrincipioAlFinalODobles() {
        assertThat(Username.isValid("-ana")).isFalse();
        assertThat(Username.isValid("ana-")).isFalse();
        assertThat(Username.isValid("ana--bea")).isFalse();
        assertThat(Username.isValid("---")).isFalse();
    }

    @Test
    void rechazaCaracteresNoPermitidos() {
        assertThat(Username.isValid("Ana")).isFalse();
        assertThat(Username.isValid("ana_bea")).isFalse();
        assertThat(Username.isValid("ana.bea")).isFalse();
        assertThat(Username.isValid("ana bea")).isFalse();
        assertThat(Username.isValid("pingüino")).isFalse();
        assertThat(Username.isValid("araña")).isFalse();
        assertThat(Username.isValid("café")).isFalse();
        assertThat(Username.isValid(null)).isFalse();
    }

    @Test
    void longitudEntre3Y39() {
        assertThat(Username.isValid("ab")).isFalse();
        assertThat(Username.isValid("abc")).isTrue();
        assertThat(Username.isValid("a".repeat(39))).isTrue();
        assertThat(Username.isValid("a".repeat(40))).isFalse();
        assertThat(Username.isValid("")).isFalse();
    }

    @Test
    void normalizaAMinusculasSinEspacios() {
        assertThat(Username.normalize("  Nutria-Dormilona-42 ")).isEqualTo("nutria-dormilona-42");
        assertThat(Username.isValid(Username.normalize("ANA"))).isTrue();
        assertThat(Username.normalize(null)).isNull();
    }
}
