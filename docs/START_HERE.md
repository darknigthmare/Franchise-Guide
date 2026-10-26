# Démarrage — FranchiseGuide V0.36.0

## Migration depuis une version antérieure

1. Exporter la bibliothèque en JSON ou `.fgbackup` et conserver la copie hors du navigateur.
2. Fermer les anciens onglets et lanceurs.
3. Cloner le dépôt ou extraire V0.36 dans un nouveau dossier.
4. Garder le même navigateur, profil et adresse locale si la bibliothèque doit être retrouvée.

Le portable reste **V11** et le conteneur protégé reste **V1**. Les check-ins actifs, rappels de sauvegarde, références de compte et états d’interface ne sont pas des données portables générales. Ne pas faire alterner deux versions sur le même stockage.

## PC

Java17 ou plus récent est requis, non embarqué. Dans la Suite : `desktop/FranchiseGuide-PC.bat` sous Windows, `bash desktop/FranchiseGuide-PC.sh` sous Linux/macOS. Dans l’archive PC séparée, les lanceurs sont à la racine. Le JAR compilé est dans `desktop/dist` ou `dist`.

L’interface s’ouvre sur `http://127.0.0.1:47631/`. Garder le lanceur actif et fermer l’ancienne version si le port est occupé. Ce n’est pas un EXE autonome. Le serveur est testé sous Linux ; la fenêtre graphique Windows/macOS ne l’est pas.

## Web personnel

Node22+ requis. Depuis la racine de la Suite ou de l’archive Web :

```sh
node server/start.cjs
```

Ouvrir `http://127.0.0.1:47632/`. Aucune installation npm n’est nécessaire pour l’usage du serveur. Les ports47631 et47632 ont des stockages distincts : transférer par export/import. Ne pas ouvrir `index.html` en `file://`. Le serveur est destiné à l’usage personnel local, pas à être publié tel quel comme service multiutilisateur.

## Check-in Trakt

Connecter Trakt dans Réglages, puis ouvrir un film ou épisode ajouté ayant un identifiant TMDb et choisir **Check-in Trakt**. La session apparaît dans **Plus → Aujourd’hui**.

- Avant la durée estimée : **Terminer** ajoute explicitement l’historique distant puis local après accusé.
- Après la durée estimée : **Vérifier la fin Trakt** lit d’abord l’historique récent, car Trakt peut déjà avoir marqué l’élément vu. Aucun second historique n’est envoyé dans ce cas.
- Réponse réseau perdue : la session devient incertaine ; utiliser **Vérifier l’historique** plutôt que relancer aveuglément.
- **Annuler** arrête le check-in sans visionnage local lorsqu’un accusé est obtenu.
- **Oublier localement** ne garantit pas l’arrêt distant ; utiliser seulement après vérification manuelle.

L’application Web/PC ne programme aucune fin lorsque la page est fermée. Le passage automatique Trakt après la durée est confirmé lors d’une vérification/synchronisation ultérieure, pas par un processus de fond FranchiseGuide.

## Aujourd’hui

Cet écran agrège les sorties et épisodes datés aujourd’hui, les titres prêts à reprendre, le check-in, les opérations Trakt à vérifier et le rappel de sauvegarde. Les résultats sont calculés depuis la bibliothèque et les réglages existants. Une donnée absente n’est pas devinée.

## Sauvegarde indépendante

Après un export, FranchiseGuide indique qu’un fichier a été **proposé** au téléchargement. Vérifier sa présence dans un emplacement externe, puis choisir **J’ai vérifié le fichier**. Sans cette confirmation, le rappel reste en attente. Le rappel est dû après quatorze jours et peut être reporté sept jours.

Cette fonction ne copie pas automatiquement vers Drive, ne vérifie pas le disque et ne remplace pas une sauvegarde externe. La confirmation reste déclarative. Pour une copie chiffrée, utiliser l’export `.fgbackup` et conserver sa phrase séparément.

## Effacement local

Dans Confidentialité, saisir `EFFACER`. Résoudre d’abord tout check-in et toute écriture Trakt active. L’opération vise les stockages FranchiseGuide de cette origine, pas les fichiers téléchargés, le compte Trakt ou les variables du serveur. Si un stockage du navigateur est inaccessible, l’application termine les autres suppressions puis demande de recharger et recommencer pour vérifier le résidu.

## Sans clés utilisateur et Android

Les sources publiques TVmaze, Apple/iTunes, FreeToGame et Open Library restent accessibles sous réserve du réseau et de leurs limites. Une fiche manuelle reste possible. Les fonctions riches exigent leurs accès autorisés ; aucun accès privé n’est livré.

Android est fourni en sources Kotlin/Compose et un APK de développement a été compilé avec le SDK 36. Le panneau Aujourd’hui, les règles de check-in, le rappel de sauvegarde et le widget sont raccordés. La recette sur appareil, les notifications réelles, le stockage SharedPreferences sur téléphone, la distribution signée et les comptes Trakt réels restent à valider. Le détail de compilation et du lint figure dans [QA_REPORT.md](QA_REPORT.md).

Voir [les check-ins et sauvegardes](CHECKINS_AUJOURDHUI_ET_SAUVEGARDES.md) et [le rapport QA](QA_REPORT.md).
