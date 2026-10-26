# Jeux, trophées et comptes — V0.14

Les fiches jeux possèdent une entrée **Trophées & succès**. Les listes restent séparées par jeu local, compte, fournisseur, plateforme et édition. Une liste PS4, PS5 ou d’un remaster conserve sa propre identité.

## Connexions et profils

| Service | Parcours livré sur Web / PC |
|---|---|
| Steam | Connexion OpenID vérifiée côté serveur personnel, import des jeux accessibles et lecture des succès avec FG_STEAM_API_KEY. Voir STEAM_SIGN_IN.md. |
| RetroAchievements | Association d’un profil public par pseudo puis ULID, import des jeux avec progression et lecture normal/Hardcore avec FG_RETRO_API_KEY. Cette association n’authentifie pas le propriétaire. Voir RETROACHIEVEMENTS.md. |
| Trakt | Connexion et suivi séries/films existants ; il ne synchronise pas les succès des jeux. |
| PSN / PSNProfiles, Xbox, Epic, GOG | Références et imports au format neutre ; aucun nouveau connecteur automatique livré. |

Les anciennes références Steam saisies manuellement ne sont pas des sessions OpenID. Aucun profil importé par JSON ne devient automatiquement un compte authentifié. Les passerelles sont fournies dans le JAR PC et le serveur Web personnel ; un hébergement purement statique ne les remplace pas.

## Listes et suivi personnel

Titre, description, miniature, grade, points, état obtenu/non obtenu/inconnu, date et rareté sont affichés lorsqu’ils sont disponibles. Les informations manquantes ne sont pas inventées. Les succès secrets restent masqués jusqu’à révélation ou déblocage. Recherche, filtres et tris s’appliquent à toute la liste ; l’affichage construit d’abord 50 entrées, puis permet de charger les suivantes.

Le jeu de base et les DLC sont distingués lorsque les relevés permettent ce classement. La lecture Steam ne fournit pas de partition DLC générale dans cette intégration. Les ensembles supplémentaires RetroAchievements gardent leur ID ; ils ne sont pas automatiquement étiquetés comme DLC.

Les modes normal et Hardcore RetroAchievements disposent de leurs états, dates et filtres. La rareté utilise des compteurs de joueurs/déblocages lorsqu’ils sont disponibles ; les RetroPoints pondérés ne deviennent pas un pourcentage. Une date absente reste inconnue.

Les notes, épingles, guides et cases de checklist restent locaux et sont conservés pendant les actualisations. Les indicateurs *online*, *manquable* ou *inaccessible* ne sont pas déduits pour tous les jeux. Certaines données peuvent venir d’un import ou du type de succès fourni par RA.

Une checklist personnelle ne débloque rien sur une plateforme. Une liste vide ne vaut pas 100 %, terminer tous les succès ne crée pas un platine absent du relevé, et les trophées ne marquent pas automatiquement une campagne comme terminée. Les temps de jeu Steam et la progression RA restent distincts du suivi personnel ; l’import RA ne coche pas la possession du jeu.

## Récupération progressive

L’import des jeux associe leurs identifiants exacts. Une ressemblance de titre n’entraîne pas de fusion automatique. Les erreurs, réponses incomplètes, changements de compte et résultats devenus obsolètes ne remplacent pas un relevé valide par des données supposées.

Une récupération initiale lance **au maximum huit listes de succès**. Si l’actualisation automatique est désactivée, les autres listes attendent une action manuelle : utiliser **Actualiser les listes Steam et RetroAchievements**, **Récupérer les jeux et succès** pour le profil concerné, ou actualiser une liste depuis la fiche jeu. Les listes les moins récemment lues sont prioritaires.

Avec l’automatisme activé, les passages suivants traitent les listes encore inconnues ou datant d’au moins six heures, pendant que l’application est ouverte, visible et connectée. Les erreurs et délais de reprise peuvent différer certaines lectures. Il n’existe pas de service permanent quand l’application est fermée. Les compteurs indiquent les listes restantes et celles en erreur.

L’espace gaming est limité à 100 profils, 2 000 listes, 5 000 succès par liste et 100 000 succès au total. Les créations de listes reportées faute de capacité sont signalées. Les quotas du navigateur peuvent être atteints avant ces limites.

## Imports, sauvegardes et Android

Le format neutre **FranchiseGuide Achievements v1** reste accepté ; il n’est pas présenté comme un export natif PSNProfiles. Les exemples fournis sont fictifs. L’import identifie sa provenance comme fichier et ne constitue pas une lecture distante vérifiée.

La sauvegarde complète reste au **schéma 8**. Elle comprend les profils, relevés, dates, notes, métadonnées Steam et alias d’identité supplémentaires, sans les clés serveur ni les cookies de session. Elle reste personnelle. Restaurer une sauvegarde désactive les automatismes gaming ; il faut les réactiver volontairement.

Les sources Android transportent ces champs et incluent la consultation des modes normal et Hardcore. La connexion et les lectures réseau natives Steam et RetroAchievements ne sont pas livrées. Les relevés peuvent être transférés par JSON depuis Web/PC. Les tests JVM ne constituent pas un APK, un build Android complet ou un essai de l’interface sur téléphone.

La V0.14 utilise des réponses fournisseurs et des stockages simulés pour ses tests d’interface DOM. Aucun compte réel ni installation PWA n’est validé ; les anciens rapports Chromium ne sont pas une validation Chromium de cette livraison. Voir QA_REPORT.md.
