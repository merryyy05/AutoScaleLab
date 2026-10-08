# AutoScaleLab - Load Testing

Ce dossier contient les tests de charge du projet AutoScaleLab réalisés avec k6.

## Objectif

k6 permet de générer une charge HTTP contrôlée afin de tester le comportement du service et, plus tard, provoquer les conditions nécessaires au déclenchement de l'auto-scaling.

## Prérequis

- k6 installé
- Service cible accessible

Vérifier l'installation :

k6 version

## Lancer le test

Depuis la racine du projet :

k6 run load-testing/load-test.js

## Scénario actuel

Le scénario de test utilise :

- 5 utilisateurs virtuels (VUs)
- durée de 10 secondes
- requêtes HTTP vers la cible de test
- vérification du code HTTP 200

## Résultat du premier test

Le premier test local a été exécuté avec succès :

- 5 VUs
- 10 secondes
- 50 itérations
- 0 échec de vérification
- 0 % de requêtes HTTP échouées

## Cible temporaire

Pour le premier test, Prometheus sur localhost:9090 est utilisé uniquement comme cible de validation.

Dans la version intégrée d'AutoScaleLab, k6 ciblera le service applicatif conteneurisé géré par Docker Swarm.

## Objectif final

Le scénario final devra permettre une montée puis une diminution contrôlée de la charge afin d'observer les métriques dans Prometheus et tester les décisions SCALE OUT / SCALE IN d'AutoScaleLab.