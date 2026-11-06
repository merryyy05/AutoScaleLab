package com.autoscalelab.autoscaler.client;

/**
 * Contrat d'accès à Docker Swarm.
 *
 * Deux implémentations existent, choisies par la propriété
 * {@code autoscaler.swarm.simulation} :
 * - {@link SimulatedSwarmClient} (true, valeur par défaut) : aucun appel Docker ;
 * - {@link DockerSwarmClient} (false) : commandes Docker réelles.
 */
public interface SwarmClient {

    /**
     * Retourne le nombre de réplicas demandé pour le service.
     *
     * @throws SwarmException si Docker ne répond pas ou si le service est introuvable
     */
    int getReplicas(String serviceName);

    /**
     * Demande à Swarm de passer le service à {@code replicas} réplicas.
     *
     * @throws IllegalArgumentException si les paramètres sont invalides
     * @throws SwarmException si la commande Docker échoue
     */
    void scaleService(String serviceName, int replicas);
}
