package com.amiva.users.domain;

import java.util.Locale;
import java.util.regex.Pattern;

// Regla del nombre de usuario (misma que ck_app_user_username): minúsculas, dígitos y guiones sueltos entre medias
public final class Username {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 39;
    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private Username() {
    }

    // Recorta espacios y pasa a minúsculas; no valida
    public static String normalize(String username) {
        return username == null ? null : username.strip().toLowerCase(Locale.ROOT);
    }

    // Comprueba el formato ya normalizado: "Ana" no es válido, normalize("Ana") sí
    public static boolean isValid(String username) {
        return username != null
                && username.length() >= MIN_LENGTH
                && username.length() <= MAX_LENGTH
                && FORMAT.matcher(username).matches();
    }
}
