package com.amiva.users.application;

import java.util.List;
import java.util.random.RandomGenerator;

import com.amiva.users.infrastructure.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Username aleatorio "animal-adjetivo-número" (nutria-dormilona-42) con concordancia de género.
// Sin tildes ni ñ para cumplir el formato de Username
@Component
public class UsernameGenerator {

    static final int MAX_ATTEMPTS = 10;
    static final int MIN_NUMBER = 10;
    static final int MAX_NUMBER = 9999;

    record Animal(String name, boolean feminine) {
    }

    record Adjective(String masculine, String feminine) {

        String agreeWith(Animal animal) {
            return animal.feminine() ? feminine : masculine;
        }
    }

    static final List<Animal> ANIMALS = List.of(
            // Masculinos
            m("pinguino"), m("gato"), m("perro"), m("oso"), m("leon"), m("tigre"), m("lobo"), m("zorro"),
            m("mapache"), m("koala"), m("panda"), m("conejo"), m("raton"), m("erizo"), m("castor"), m("tejon"),
            m("buho"), m("loro"), m("pato"), m("ganso"), m("cisne"), m("pulpo"), m("calamar"), m("cangrejo"),
            m("delfin"), m("caracol"), m("camello"), m("canguro"), m("cocodrilo"), m("hipopotamo"),
            m("rinoceronte"), m("elefante"), m("jabali"), m("ciervo"), m("alce"), m("bisonte"), m("burro"),
            m("caballo"), m("cerdo"), m("cordero"), m("gallo"), m("pavo"), m("flamenco"), m("pelicano"),
            m("colibri"), m("tucan"), m("halcon"), m("sapo"), m("lemur"), m("perezoso"), m("armadillo"),
            m("ornitorrinco"), m("suricato"), m("hamster"), m("huron"), m("topo"), m("murcielago"),
            m("escarabajo"), m("grillo"), m("avestruz"),
            // Femeninos
            f("nutria"), f("foca"), f("morsa"), f("ballena"), f("orca"), f("jirafa"), f("cebra"), f("llama"),
            f("alpaca"), f("vaca"), f("oveja"), f("cabra"), f("gallina"), f("rana"), f("tortuga"), f("ardilla"),
            f("liebre"), f("comadreja"), f("marmota"), f("chinchilla"), f("cobaya"), f("iguana"),
            f("salamandra"), f("lagartija"), f("mariposa"), f("abeja"), f("hormiga"), f("mariquita"),
            f("luciernaga"), f("libelula"), f("arana"), f("medusa"), f("sepia"), f("gaviota"), f("paloma"),
            f("ciguena"), f("grulla"), f("urraca"), f("lechuza"), f("golondrina"), f("codorniz"), f("perdiz"),
            f("hiena"), f("pantera"), f("mofeta"), f("zarigueya"), f("capibara"), f("garza"), f("anguila"),
            f("sardina"), f("trucha"), f("langosta"), f("gamba"), f("almeja"), f("ostra"), f("polilla"),
            f("mangosta"), f("pulga"));

    static final List<Adjective> ADJECTIVES = List.of(
            o("despistad"), a("dormilon"), a("jugueton"), a("gloton"), o("curios"), o("travies"),
            a("saltarin"), a("bailarin"), a("cantarin"), a("parlanchin"), a("charlatan"), a("comilon"),
            o("presumid"), o("testarud"), o("mimos"), o("frioler"), o("perezos"), same("sonriente"),
            same("valiente"), same("alegre"), same("feliz"), same("veloz"), same("audaz"), same("tenaz"),
            same("sagaz"), o("risuen"), o("simpatic"), o("carinos"), o("tranquil"), o("distraid"),
            o("olvidadiz"), a("madrugador"), a("trasnochador"), o("aventurer"), o("viajer"), o("pensativ"),
            a("sonador"), o("filosof"), same("elegante"), o("coquet"), o("chistos"), o("gracios"),
            same("bromista"), same("cotilla"), o("sabi"), o("list"), o("astut"), o("ingenios"), o("creativ"),
            o("misterios"), o("educad"), same("puntual"), o("ordenad"), o("desordenad"), o("golos"),
            o("hambrient"), o("pelud"), o("esponjos"), same("suave"), same("brillante"), o("galactic"),
            o("cosmic"), o("intrepid"), o("legendari"), o("epic"), o("magic"), same("musical"),
            o("dicharacher"), o("timid"), o("vergonzos"), o("sonrojad"), o("ruidos"), o("silencios"),
            o("sigilos"), o("veranieg"), o("player"), o("montaner"), o("futboler"), o("cafeter"),
            o("chocolater"), o("zalamer"), a("remolon"), a("tragon"), o("fiester"), o("marchos"),
            a("molon"), same("optimista"), same("entusiasta"), same("deportista"), o("romantic"),
            o("dramatic"), o("heroic"), o("espabilad"), o("resfriad"), o("estupend"),
            o("salad"), o("encantad"), o("asombrad"), o("sorprendid"));

    private final AppUserRepository users;
    private final RandomGenerator random;

    @Autowired
    public UsernameGenerator(AppUserRepository users) {
        this(users, RandomGenerator.getDefault());
    }

    UsernameGenerator(AppUserRepository users, RandomGenerator random) {
        this.users = users;
        this.random = random;
    }

    // Prueba otra combinación si ya existe; tras MAX_ATTEMPTS se rinde
    public String generateUnique() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = generate();
            if (!users.existsByUsernameIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar un username libre tras " + MAX_ATTEMPTS + " intentos");
    }

    String generate() {
        Animal animal = ANIMALS.get(random.nextInt(ANIMALS.size()));
        Adjective adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        int number = random.nextInt(MIN_NUMBER, MAX_NUMBER + 1);
        return animal.name() + "-" + adjective.agreeWith(animal) + "-" + number;
    }

    private static Animal m(String name) {
        return new Animal(name, false);
    }

    private static Animal f(String name) {
        return new Animal(name, true);
    }

    // despistad -> despistado/despistada
    private static Adjective o(String stem) {
        return new Adjective(stem + "o", stem + "a");
    }

    // dormilon, saltarin, charlatan, madrugador -> forma femenina añadiendo "a"
    private static Adjective a(String masculine) {
        return new Adjective(masculine, masculine + "a");
    }

    // Misma forma en los dos géneros (valiente, feliz)
    private static Adjective same(String form) {
        return new Adjective(form, form);
    }
}
