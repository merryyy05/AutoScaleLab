# AutoScaleLab — Monitoring et Autoscaling avec Docker Swarm

## 1. Présentation du projet

AutoScaleLab est un projet DevOps qui vise à surveiller les performances d'une application et à automatiser l'ajustement du nombre de réplicas Docker en fonction de la charge.

Le projet combine le monitoring, les tests de charge et l'orchestration des conteneurs.

## 2. Objectifs

- Déployer une application avec Docker Swarm.
- Surveiller les ressources CPU et mémoire.
- Centraliser les métriques avec Prometheus.
- Développer un autoscaler avec Spring Boot.
- Simuler une charge utilisateur avec k6.
- Étudier les mécanismes Scale Out et Scale In.

## 3. Technologies utilisées

| Technologie | Rôle |
|---|---|
| Docker | Conteneurisation |
| Docker Swarm | Gestion des services et réplicas |
| Prometheus | Collecte et consultation des métriques |
| cAdvisor | Monitoring des conteneurs |
| Python | Exporteur de métriques Docker |
| Java / Spring Boot | Développement de l'autoscaler |
| k6 | Tests de charge |
| Git / GitHub | Gestion des versions et collaboration |

## 4. Architecture du projet

Le fonctionnement prévu est le suivant :

1. Docker Swarm déploie l'application Nginx.
2. Les outils de monitoring collectent les métriques.
3. Prometheus centralise les données.
4. L'autoscaler Spring Boot interroge Prometheus.
5. L'autoscaler décide d'augmenter ou de réduire les réplicas selon la charge.
6. k6 génère du trafic pour tester le comportement du système.

## 5. Organisation de l'équipe

**Membre 1 — Autoscaler**

Développement du backend Spring Boot, récupération des métriques et logique de décision Scale Out / Scale In.

**Membre 2 — Docker Swarm**

Configuration du service Nginx, initialisation du cluster et scripts de déploiement.

**Membre 3 — Monitoring et Load Testing**

Configuration de Prometheus et cAdvisor, développement d'un exporteur Python et réalisation des tests de charge avec k6.

## 6. Tests réalisés

### Monitoring

- Prometheus accessible sur le port 9090.
- cAdvisor configuré et accessible.
- Exporteur Python fonctionnel pour le conteneur Nginx de test.
- Vérification des cibles Prometheus avec l'état UP.
- Consultation des métriques CPU et mémoire.

### Load Testing

Deux scénarios k6 ont été réalisés :

- Test simple : 5 utilisateurs virtuels pendant 10 secondes.
- Test progressif : augmentation jusqu'à 20 utilisateurs virtuels, puis diminution progressive.

**Résultat du test progressif :**

- 451 requêtes HTTP.
- 100 % des vérifications réussies.
- 0 % d'échecs HTTP.
- Durée d'environ 50 secondes.

Ces résultats valident le scénario de charge sur l'application Nginx de test, mais ne démontrent pas à eux seuls le fonctionnement automatique du scaling.

## 7. Limites et points d'intégration

Dans l'environnement Docker Desktop utilisé, cAdvisor n'a pas permis de récupérer correctement les métriques individuelles du conteneur Nginx.

Un exporteur Python utilisant `docker stats` a été développé comme solution temporaire.

L'exporteur actuel surveille un conteneur de test précis. Une adaptation est nécessaire pour mesurer les réplicas Docker Swarm.

La version examinée de l'autoscaler utilise encore une valeur CPU simulée. La connexion de la décision de scaling aux métriques réelles reste à confirmer.

## 8. Structure du projet

Les différentes parties sont développées sur des branches Git :

- `feature/autoscaler`
- `feature/swarm`
- `feature/monitoring`
- `feature/load-testing`
- `feature/documentation`

Leur intégration dans `main` doit être réalisée après validation des Pull Requests.

## 9. Conclusion

AutoScaleLab permet de mettre en pratique plusieurs concepts DevOps : conteneurisation, orchestration, monitoring, tests de charge et automatisation.

Le projet constitue une base pour expérimenter et améliorer un système d'autoscaling horizontal basé sur des métriques réelles.