package com.autoscalelab.autoscaler.service;

/**
 * Levée quand Prometheus est injoignable ou renvoie une métrique inutilisable.
 */
public class MetricsUnavailableException extends RuntimeException {

    public MetricsUnavailableException(String message) {
        super(message);
    }

    public MetricsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
