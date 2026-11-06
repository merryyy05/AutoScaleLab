# AutoScaleLab - Load Testing

Ce dossier contient les tests de charge k6 qui provoquent le scaling automatique.

## Prérequis

- k6 installé (`k6 version`)
- Stack Swarm déployée : Nginx répond sur http://localhost:8080

## Scénarios

| Fichier | Charge | Durée | Usage |
|---|---|---|---|
| `load-test.js` | 5 VUs, 1 requête/s chacun | 10 s | Vérifier que Nginx répond |
| `ramp-test.js` | 0 → 50 → 200 VUs, sans pause | 7 min 30 s | **Démonstration du scaling** |

`ramp-test.js` :

| Étape | Durée | VUs | But |
|---|---|---|---|
| Montée | 1 min | 0 → 50 | Charge progressive |
| Charge forte | 1 min | 50 → 200 | Le CPU dépasse 75 % → SCALE UP |
| Plateau | 5 min | 200 | Ajout des réplicas (1 par minute, cooldown 60 s) |
| Arrêt | 30 s | 200 → 0 | Fin de charge |

Seuil de réussite : moins de 5 % de requêtes en échec (`http_req_failed`).
Après le test, attendre 2 à 3 minutes sans charge pour observer les SCALE DOWN.

## Lancement

Depuis la racine du projet :

```powershell
k6 run load-testing/load-test.js
k6 run load-testing/ramp-test.js
```

Autre cible : `k6 run -e TARGET_URL=http://localhost:8080 load-testing/ramp-test.js`

Vérifier un script sans le lancer : `k6 inspect load-testing/ramp-test.js`

## Précautions

`ramp-test.js` consomme beaucoup de CPU sur le PC (k6 + Docker). Sur un PC
de 8 Go, fermer les applications lourdes avant la démonstration et garder
au moins 1 Go de RAM libre. Le test est borné dans le temps ; Ctrl+C l'arrête.

Les résultats de la démonstration réelle sont dans le README principal.
