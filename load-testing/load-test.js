import http from 'k6/http';
import { check, sleep } from 'k6';

// Test rapide : vérifie que l'application Nginx de la stack Swarm répond.
//
// Lancement :  k6 run load-testing/load-test.js
// Autre cible : k6 run -e TARGET_URL=http://localhost:8080 load-testing/load-test.js

const TARGET_URL = __ENV.TARGET_URL || 'http://localhost:8080';

export const options = {
    vus: 5,
    duration: '10s',
};

export default function () {

    const response = http.get(TARGET_URL);

    check(response, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(1);
}
