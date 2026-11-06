package com.autoscalelab.autoscaler.client;

/**
 * Erreur levée quand une opération Docker / Swarm échoue
 * (Docker absent, délai dépassé, Swarm inactif, service introuvable...).
 */
public class SwarmException extends RuntimeException {

    public SwarmException(String message) {
        super(message);
    }

    public SwarmException(String message, Throwable cause) {
        super(message, cause);
    }
}
