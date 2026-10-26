# FranchiseGuide Web/PWA 0.36.0

Moteur Web canonique avec check-ins Trakt récupérables, écran Aujourd’hui et rappel de sauvegarde externe. Lancer depuis la distribution Web ou la Suite avec Node22+ : `node server/start.cjs`, puis `http://127.0.0.1:47632/`. Ne pas utiliser file://.

Après la durée estimée d’un check-in, l’application vérifie l’historique Trakt avant toute nouvelle écriture pour éviter une relecture en double. Aucun traitement Web fermé n’est programmé. Le rappel de sauvegarde est déclaratif et ne copie rien automatiquement vers Drive.

Avant migration, exporter la bibliothèque et fermer les anciens onglets. Les instructions courantes sont dans le README et START_HERE.md à la racine du dépôt, et les résultats dans docs/QA_REPORT.md. La recette Chrome de cette version utilise une vraie origine, IndexedDB, deux onglets et le mode hors ligne. L’installation PWA et les autorisations de comptes réels restent à valider.
