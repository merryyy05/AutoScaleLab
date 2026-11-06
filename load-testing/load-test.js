import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 5,
    duration: '10s',
};

export default function () {

    const response = http.get('http://localhost:9090');

    check(response, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(1);
}