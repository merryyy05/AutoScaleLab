package com.autoscalelab.autoscaler.service;

import java.time.Instant;

/**
 * Résultat d'un cycle de vérification.
 *
 * @param timestamp      moment de la décision
 * @param action         SCALE_UP, SCALE_DOWN ou NO_ACTION
 * @param cpu            CPU mesuré (null si indisponible)
 * @param replicasBefore réplicas lus dans Swarm (null si illisibles)
 * @param replicasAfter  réplicas après la décision (null si illisibles)
 * @param reason         explication lisible
 * @param error          true si le cycle a rencontré une erreur (aucun changement)
 */
public record ScalingDecision(
        Instant timestamp,
        Action action,
        Double cpu,
        Integer replicasBefore,
        Integer replicasAfter,
        String reason,
        boolean error) {

    public enum Action {
        SCALE_UP("SCALE UP"),
        SCALE_DOWN("SCALE DOWN"),
        NO_ACTION("NO ACTION");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    static ScalingDecision scaled(Instant now, double cpu, int before, int after, String reason) {
        Action action = after > before ? Action.SCALE_UP : Action.SCALE_DOWN;
        return new ScalingDecision(now, action, cpu, before, after, reason, false);
    }

    static ScalingDecision noAction(Instant now, double cpu, int replicas, String reason) {
        return new ScalingDecision(now, Action.NO_ACTION, cpu, replicas, replicas, reason, false);
    }

    static ScalingDecision failure(Instant now, Double cpu, Integer replicas, String reason) {
        return new ScalingDecision(now, Action.NO_ACTION, cpu, replicas, replicas, reason, true);
    }

    /** true si le nombre de réplicas a été modifié. */
    public boolean changed() {
        return action != Action.NO_ACTION;
    }

    /** Ligne de log, par exemple "SCALE UP : cpu=82,4 % > 75 %, réplicas 2 → 3". */
    public String summary() {
        return action.label() + " : " + reason;
    }
}
