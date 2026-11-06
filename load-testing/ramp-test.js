import http from 'k6/http';
import { check } from 'k6';

// Charge soutenue sur l'application Nginx de la stack Swarm (port 8080).
//
// L'AutoScaler ajoute au plus 1 réplica par minute (cooldown 60 s) :
// passer de 1 à 5 réplicas demande environ 4 minutes de charge forte.
// Après le test, attendre ~5 minutes sans charge pour observer les SCALE DOWN.
//
// Lancement :  k6 run load-testing/ramp-test.js
// Autre cible : k6 run -e TARGET_URL=http://localhost:8080 load-testing/ramp-test.js

const TARGET_URL = __ENV.TARGET_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '1m', target: 50 },    // montée progressive
    { duration: '1m', target: 200 },   // charge forte
    { duration: '5m', target: 200 },   // plateau : phase de SCALE UP
    { duration: '30s', target: 0 },    // arrêt de la charge
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],    // moins de 5 % d'erreurs
  },
};

export default function () {
  const response = http.get(TARGET_URL);

  check(response, {
    'status is 200': (r) => r.status === 200,
  });
  // Pas de sleep : chaque utilisateur virtuel envoie des requêtes en continu.
}
