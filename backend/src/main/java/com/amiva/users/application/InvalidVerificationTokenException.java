package com.amiva.users.application;

// Enlace de verificación que no sirve. reason indica por qué para que el front muestre el mensaje adecuado
public class InvalidVerificationTokenException extends RuntimeException {

    public enum Reason {
        INVALID, EXPIRED, USED
    }

    private final Reason reason;

    public InvalidVerificationTokenException(Reason reason) {
        super(switch (reason) {
            case INVALID -> "El enlace de verificación no es válido";
            case EXPIRED -> "El enlace de verificación ha caducado";
            case USED -> "El enlace de verificación ya se ha usado";
        });
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
