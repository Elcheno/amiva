package com.amiva.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amiva.users.application.UsernameGenerator.Adjective;
import com.amiva.users.application.UsernameGenerator.Animal;
import com.amiva.users.domain.Username;
import com.amiva.users.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsernameGeneratorTest {

    private static final Map<String, Animal> ANIMALS_BY_NAME = UsernameGenerator.ANIMALS.stream()
            .collect(Collectors.toMap(Animal::name, Function.identity()));
    private static final Set<String> MASCULINE = UsernameGenerator.ADJECTIVES.stream()
            .map(Adjective::masculine).collect(Collectors.toSet());
    private static final Set<String> FEMININE = UsernameGenerator.ADJECTIVES.stream()
            .map(Adjective::feminine).collect(Collectors.toSet());

    @Mock
    AppUserRepository users;

    @Test
    void generaUsernamesValidosConConcordanciaDeGenero() {
        UsernameGenerator generator = new UsernameGenerator(users, new Random(42));

        for (int i = 0; i < 10_000; i++) {
            String username = generator.generate();
            assertThat(Username.isValid(username)).as(username).isTrue();
            assertThat(username.length()).as(username).isLessThanOrEqualTo(Username.MAX_LENGTH);

            String[] parts = username.split("-");
            assertThat(parts).as(username).hasSize(3);
            Animal animal = ANIMALS_BY_NAME.get(parts[0]);
            assertThat(animal).as(username).isNotNull();
            assertThat(animal.feminine() ? FEMININE : MASCULINE).as(username).contains(parts[1]);
            assertThat(Integer.parseInt(parts[2])).as(username)
                    .isBetween(UsernameGenerator.MIN_NUMBER, UsernameGenerator.MAX_NUMBER);
        }
    }

    @Test
    void mismaSemillaMismaSecuencia() {
        UsernameGenerator a = new UsernameGenerator(users, new Random(7));
        UsernameGenerator b = new UsernameGenerator(users, new Random(7));

        for (int i = 0; i < 100; i++) {
            assertThat(a.generate()).isEqualTo(b.generate());
        }
    }

    // Peor caso: la combinación más larga posible
    @Test
    void elMasLargoPosibleCabeEn39() {
        int longestAnimal = UsernameGenerator.ANIMALS.stream().mapToInt(a -> a.name().length()).max().orElseThrow();
        int longestAdjective = Stream.concat(MASCULINE.stream(), FEMININE.stream())
                .mapToInt(String::length).max().orElseThrow();
        int longestNumber = String.valueOf(UsernameGenerator.MAX_NUMBER).length();

        assertThat(longestAnimal + longestAdjective + longestNumber + 2).isLessThanOrEqualTo(Username.MAX_LENGTH);
    }

    @Test
    void listasSinTildesNiEnesNiDuplicados() {
        List<String> animals = UsernameGenerator.ANIMALS.stream().map(Animal::name).toList();
        List<String> adjectives = UsernameGenerator.ADJECTIVES.stream().map(Adjective::masculine).toList();

        assertThat(animals).doesNotHaveDuplicates().allMatch(w -> w.matches("[a-z]+"), "solo a-z");
        assertThat(adjectives).doesNotHaveDuplicates();
        assertThat(UsernameGenerator.ADJECTIVES.stream().map(Adjective::feminine).toList()).doesNotHaveDuplicates();
        assertThat(Stream.concat(MASCULINE.stream(), FEMININE.stream()))
                .allMatch(w -> w.matches("[a-z]+"), "solo a-z");
        assertThat(animals).hasSizeGreaterThanOrEqualTo(100);
        assertThat(adjectives).hasSizeGreaterThanOrEqualTo(90);
        // Hay animales de los dos géneros
        assertThat(UsernameGenerator.ANIMALS).anyMatch(Animal::feminine).anyMatch(a -> !a.feminine());
    }

    @Test
    void reintentaSiYaExiste() {
        UsernameGenerator generator = new UsernameGenerator(users, new Random(1));
        Set<String> tried = new HashSet<>();
        when(users.existsByUsernameIgnoreCase(anyString())).thenAnswer(inv -> tried.add(inv.getArgument(0))
                && tried.size() < 3);

        String username = generator.generateUnique();

        assertThat(Username.isValid(username)).isTrue();
        verify(users, times(3)).existsByUsernameIgnoreCase(anyString());
    }

    @Test
    void lanzaSiSeAgotanLosIntentos() {
        UsernameGenerator generator = new UsernameGenerator(users, new Random(1));
        when(users.existsByUsernameIgnoreCase(anyString())).thenReturn(true);

        assertThatThrownBy(generator::generateUnique).isInstanceOf(IllegalStateException.class);
        verify(users, times(UsernameGenerator.MAX_ATTEMPTS)).existsByUsernameIgnoreCase(anyString());
    }
}
