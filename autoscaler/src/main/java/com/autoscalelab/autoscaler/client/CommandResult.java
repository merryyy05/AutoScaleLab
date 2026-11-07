package com.autoscalelab.autoscaler.client;

/**
 * Résultat d'une commande système : code de sortie, sortie standard et sortie d'erreur.
 */
public record CommandResult(int exitCode, String stdout, String stderr) {

    public boolean isSuccess() {
        return exitCode == 0;
    }
}
