# FranchiseGuide V0.20 — démarrage et mise à jour

## Avant la mise à jour

Exporter un JSON depuis la V0.19 et le conserver ailleurs que dans le profil du navigateur. Fermer les anciens onglets et lanceurs. Extraire la nouvelle version dans un dossier distinct. Conserver le même navigateur/profil et la même adresse locale pour retrouver les données ; sur une autre origine, utiliser l’import avec aperçu.

**Le nouvel export est au schéma V9.** V0.20 accepte les anciens schémas V1 à V8. Ne pas relancer une ancienne version dans le même profil après migration et ne pas lui demander de réimporter le V9. Les nouveaux champs de parcours ne doivent pas être perdus lors d’un réenregistrement ancien.

Les clés, sessions, files d’envoi et délais opérationnels ne sont pas inclus dans l’export portable. Les préférences peuvent être conservées ou reprises explicitement dans l’aperçu d’import ; un fichier portable ne connecte pas un compte.

## PC — Java 17 ou supérieur

Dans la distribution PC séparée, lancer `FranchiseGuide-PC.bat` sous Windows ou `bash FranchiseGuide-PC.sh` sous Linux/macOS. Dans la Suite, ces fichiers sont sous `desktop/`.

Le programme démarre un serveur personnel sur **http://127.0.0.1:47631/** et ouvre le navigateur. Garder le lanceur ouvert. Java n’est pas embarqué ; aucun EXE natif n’est fourni. Le JAR est compilé en bytecode Java17, avec des contrôles HTTP locaux. La fenêtre graphique Windows/macOS et l’installateur ne sont pas validés.

## Web — Node 22 ou supérieur

Depuis la Suite ou la distribution Web séparée :

```bash
node server/start.cjs
```

Ouvrir **http://127.0.0.1:47632/**. Aucune installation npm n’est requise pour utiliser ce serveur personnel. Un hébergement statique ne fournit pas les passerelles Steam/RetroAchievements. Aucun site public n’est créé par cette livraison.

Les ports 47631 et 47632 sont deux origines : ils ne partagent pas automatiquement les données du navigateur. L’import JSON est prévu pour les transferts. La PWA et ses ressources sont fournies ; installation réelle, fonctionnement hors ligne et persistance dans un profil installé restent à vérifier sur le navigateur cible.

## Progression actuelle et fins déjà enregistrées

Dans une fiche **Jeu** ou **Livre**, le panneau « Mon parcours actuel » sépare l’avancement des fins historiques. Utiliser 0 si le total est inconnu. Choisir l’unité : pages, chapitres, %, missions… Ne pas mélanger deux unités dans un même compteur.

- Diminuer l’avancement, mettre en pause ou abandonner conserve les anciennes fins et leurs dates.
- « Commencer une relecture / nouvelle partie » remet l’avancement à zéro et garde l’historique.
- Atteindre un total connu, ou choisir Terminé, enregistre une fin si le parcours n’était pas déjà terminé. Sauvegarder de nouveau le même état ne crée pas de lecture supplémentaire.
- L’action d’effacement des fins reste distincte et confirmée. Ce n’est pas le bouton de reprise.

La plateforme personnelle, l’édition/format et les minutes réellement jouées sont des champs locaux. Une durée moyenne RAWG n’est pas prise pour ton temps joué. Laisser les minutes vides pour « inconnu » ; 0 est une valeur connue différente.

## Statistiques et historique

Plus → Statistiques ou Historique. Les filtres de catégorie, favoris, masqués et spéciaux s’appliquent à la sélection. La période limite les événements datés uniquement, pas le nombre de titres possédés/suivis ni le relevé de succès ou d’heures actuel.

Les dates futures sont écartées des graphiques et signalées comme non exploitables ; les anciens compteurs sans dates restent présents sans répartition inventée dans le calendrier. Les films/épisodes sans durée positive connue ne reçoivent pas automatiquement 90/45 minutes dans les statistiques. Les titres ont la durée catalogue actuelle, pas une mesure exacte de la séance.

L’export JSON du bilan contient les compteurs, pas les titres ni les profils. L’export détaillé JSON/CSV contient les titres visibles et leurs dates : le traiter comme personnel. Il n’exporte pas les notes privées ou secrets, et n’est pas une sauvegarde. Au-delà de 10 000 événements, réduire les filtres ; le fichier n’est pas tronqué silencieusement. L’historique affiche 100 événements au départ, puis permet d’en charger davantage.

## Entretien catalogue — Web/PC

Plus → Entretien des fiches catalogue. Il s’agit des métadonnées TMDb/RAWG/Open Library ; Trakt et les succès Steam/RetroAchievements ont leur propre centre.

« Compléter jusqu’à 8 fiches » traite un lot fini. « Actualiser selon l’ancienneté » inclut les fiches plus anciennes : 1 jour pour une série active, 90 jours pour une série terminée, 30 jours pour les autres catalogues. Ces valeurs sont des choix de FranchiseGuide, pas des quotas promis par les fournisseurs. Une passe automatique lit au maximum 3 fiches pendant que l’application est visible/connectée et si l’option catalogue est active.

Une erreur met la fiche en attente de 5 à 60 minutes ; HTTP429 impose le délai du fournisseur (au moins 60 secondes, au plus 24 heures). Un clic manuel, y compris le changement de langue d’une fiche, ne contourne pas cette attente. Après une annulation, les fiches déjà enregistrées restent à jour et le reste n’est pas traité. Relancer un nouveau lot pour continuer.

Une affiche manquante peut aussi manquer chez le fournisseur. Si la fiche est complète, cela ne provoque pas des requêtes en boucle. Les fiches locales/Steam/Retro sans identifiant catalogue compatible restent hors de cette réparation ; aucun rapprochement par titre seul n’est tenté.

## Catalogues et comptes

Les clés personnelles TMDb/RAWG et identifiants Trakt se configurent dans les réglages. Steam et RetroAchievements utilisent les variables serveur décrites dans les guides existants. Ne pas publier un Client Secret partagé dans un site statique. Aucun identifiant privé n’est inclus. Sans clé et réseau appropriés, aucune miniature ni fiche complète du catalogue concerné n’est promise.

Les relevés de succès ne terminent pas les campagnes. Les heures Steam restent séparées par profil/App ID ; elles ne sont pas additionnées aux minutes manuelles ni aux durées vidéo. Trakt ne fournit pas de cloud général jeux/livres/franchises.

## Android et statut de livraison

Sources dans la Suite, **0.20.0 / versionCode20**. Lire `docs/ANDROID_BUILD.md`. Aucun APK/AAB, build SDK/Compose ou test téléphone. Les nouveaux filtres/copies sont décrits avec leurs différences dans `docs/FEATURE_MATRIX.md`.

Conserver une vraie copie JSON hors de l’application : les snapshots locaux ne protègent pas contre l’effacement du profil entier. Le projet reste une version de développement, pas un produit commercial déclaré complet ou sans bug.
