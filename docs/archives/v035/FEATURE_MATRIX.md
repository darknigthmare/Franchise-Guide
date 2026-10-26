# Fonctions et limites — V0.35

| Fonction | Web / PC | Android source |
|---|---|---|
| Démarrer check-in film/épisode | UI et règles testées avec Trakt simulé | Repository, client et panneau raccordés ; compte réel non testé |
| Session persistante liée au compte/application | localStorage borné, récupération brute | SharedPreferences source, pas d’essai disque/appareil |
| Fin volontaire avant durée | historique puis suivi local après accusé | Même ordre source |
| Durée atteinte | lecture d’historique avant toute écriture ; aucun POST doublon | Même règle source |
| Réponse perdue | état incertain, vérification explicite | état persistant et action source |
| Annulation/nettoyage | actions distinctes, aucun suivi supposé | actions source |
| Fin automatique application fermée | **Non** | **Non validée** ; pas de tâche de fond livrée |
| Aujourd’hui | check-in, sauvegarde, synchro, agenda, prochains | panneau et widget source ; pas de test appareil |
| Rappel sauvegarde14j / report7j | présent, confirmation déclarative | règles et panneau source |
| Auto Backup hebdomadaire vers destination externe | **Non** | **Non** ; export manuel/protégé existant |
| Effacement local exhaustif | contrôlé et vérifié autant que l’origine le permet | pas d’écran équivalent ajouté/validé |
| Portable V11 / `.fgbackup` V1 | inchangés | inchangés dans les sources |

## Parité SeriesGuide déjà présente ou approchée

Quatre médias, listes, saisons/épisodes, historique daté et relectures, collection/watchlist/notes Trakt, check-in, avis, statistiques, calendrier, langues de titre, imports SeriesGuide, sauvegardes fichiers, widgets/notifications sources, franchises, disponibilité, ISBN/éditions et progression jeux/livres.

## Principaux écarts encore ouverts

- SeriesGuide Cloud et synchronisation intégrale multiappareil.
- Auto Backup périodique Android avec copie SAF hors appareil et restauration UI native testée.
- Exécution de check-in/synchronisation en tâche de fond et mise à jour immédiate widget après sync réelle.
- Build Android SDK/Compose, APK/AAB, appareil, WorkManager, notifications et widget.
- Comptes fournisseurs réels, quotas, verrouillages et états serveur réels.
- PWA installée, stockage persistant d’origine et concurrence multi-onglets réelle.
- Connecteurs authentifiés PSN/Xbox/Epic/GOG/PSNProfiles : références manuelles uniquement lorsque prévues.
- Backend cloud général, chiffrement de la base active, gestion commerciale et audits indépendants.

Les catalogues publics sans clé restent limités à leur périmètre. Une réussite de test avec réponse simulée ne garantit ni la disponibilité du fournisseur ni une parité complète. Les accès locaux ne sont pas chiffrés par les sauvegardes protégées ; celles-ci protègent seulement le fichier exporté.
